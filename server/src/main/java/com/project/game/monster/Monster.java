package com.project.game.monster;

import com.project.game.map.Zone;
import com.project.game.monster.MonsterTemplate.Spawn;
import com.project.game.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Quái: tự hồi sinh, di chuyển, đánh, bị thương, chết và tự báo cho khu vực qua {@code zone.service()}.
 * Chạy trên writer của Zone nên không cần lock.
 */
public final class Monster {
    private static final int STATUS_LIVE = 0;
    private static final int STATUS_DIE = 1;
    private static final int MOVE_TYPE_RUN = 1;
    private static final int INITIAL_MOVE_DIR = 1;
    private static final int MOVEMENT_STEP_MULTIPLIER = 4;
    private static final int CHASE_LEASH = 1_200;
    private static final int ATTACK_RANGE = 900;
    private static final long NO_RESPAWN = -1L;

    private final MonsterTemplate template;
    private final int id;
    private final int type;
    private final int level;
    private final int levelStatus;
    private final int xFirst;
    private final int yFirst;
    private final long maxHp;

    private int x;
    private int y;
    private long hp;
    private int status;
    private long respawnAtMillis = NO_RESPAWN;
    private int moveDir = INITIAL_MOVE_DIR;
    private final LinkedHashSet<Integer> enemies = new LinkedHashSet<>();
    private long lastAttackAtMillis;
    private Zone zone;

    Monster(Spawn spawn, MonsterTemplate template) {
        Objects.requireNonNull(spawn, "spawn");
        this.template = Objects.requireNonNull(template, "template");
        if (template.id() != spawn.templateId()) {
            throw new IllegalArgumentException("monster template does not match spawn");
        }
        if (template.damage() <= 0L) {
            throw new IllegalArgumentException("monster damage must be positive");
        }
        if (template.rangeMove() < 0) {
            throw new IllegalArgumentException("monster movement range must be non-negative");
        }
        if (template.speed() < 0) {
            throw new IllegalArgumentException("monster movement speed must be non-negative");
        }
        id = spawn.id();
        type = spawn.type();
        level = spawn.level();
        levelStatus = spawn.levelStatus();
        xFirst = spawn.x();
        yFirst = spawn.y();
        x = spawn.x();
        y = spawn.y();
        maxHp = spawn.maxHp();
        hp = spawn.hp();
        status = spawn.status();
    }

    public Zone zone() {
        return zone;
    }

    /** Chỉ Zone gọi khi nhận Monster. */
    public void enterZone(Zone zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Một nhịp của Monster: chết thì chờ hồi sinh; sống thì di chuyển rồi đánh. */
    public void update(long nowMillis, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        if (!isAlive()) {
            respawn(nowMillis);
            return;
        }
        List<Player> enemyPlayers = enemyPlayers();
        updateMove(enemyPlayers);
        updateAttack(enemyPlayers, nowMillis, random);
    }

    /** Player đánh trúng: trừ HP, ghi thù; HP về 0 thì chết. */
    public void injure(Player attacker, long damage, long nowMillis) {
        Objects.requireNonNull(attacker, "attacker");
        if (damage <= 0L) {
            return;
        }
        if (!isAlive()) {
            return; // Writer tuần tự: chỉ một đòn đưa HP về 0.
        }
        long hpAfter = Math.max(0L, hp - damage);
        if (hpAfter == 0L) {
            die(attacker, damage, nowMillis);
            return;
        }
        hp = hpAfter;
        enemies.add(attacker.id());
        zone.service().monsterInjure(this, damage);
    }

    private void die(Player killer, long damage, long nowMillis) {
        long respawnAt = Math.addExact(nowMillis, respawnDelayMillis(zone.playerCount()));
        hp = 0L;
        enemies.add(killer.id());
        status = STATUS_DIE;
        respawnAtMillis = respawnAt;
        zone.service().monsterStartDie(this, damage);

        long reward = template.potentialReward();
        if (reward > 0L) {
            killer.addPotential(reward);
            zone.service().playerPotential(killer);
        }
    }

    /** Hồi sinh tại chỗ xuất phát khi đã qua hạn (so sánh nghiêm ngặt). */
    boolean respawn(long nowMillis) {
        if (status != STATUS_DIE || respawnAtMillis == NO_RESPAWN || nowMillis <= respawnAtMillis) {
            return false;
        }

        x = xFirst;
        y = yFirst;
        hp = maxHp;
        status = STATUS_LIVE;
        respawnAtMillis = NO_RESPAWN;
        enemies.clear();
        lastAttackAtMillis = 0L;
        moveDir = INITIAL_MOVE_DIR;
        zone.service().monsterRespawn(this);
        return true;
    }

    /** Các Player còn sống trong Zone mà Monster đang thù. */
    private List<Player> enemyPlayers() {
        List<Player> players = new ArrayList<>();
        for (int playerId : enemies) {
            Player player = zone.findPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (player.isDead()) {
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private static long respawnDelayMillis(int playerCount) {
        return Math.max(10_000L - 1_000L * playerCount, 5_000L);
    }

    /** Đuổi theo kẻ thù gần nhất trong tầm đuổi, không có thì đi tuần. */
    boolean updateMove(List<Player> hostilePlayers) {
        Objects.requireNonNull(hostilePlayers, "hostilePlayers");
        if (!isAlive() || template.type() != MOVE_TYPE_RUN) {
            return false;
        }

        Player target = null;
        long targetDistance = Long.MAX_VALUE;
        for (Player player : hostilePlayers) {
            if (player == null) {
                continue;
            }
            if (isWithinAttackRange(player)) {
                return false;
            }
            if (Math.abs((long) player.x() - xFirst) > CHASE_LEASH) {
                continue;
            }
            long distance = squaredDistance(player);
            if (target == null
                    || distance < targetDistance
                    || distance == targetDistance && player.id() < target.id()) {
                target = player;
                targetDistance = distance;
            }
        }
        boolean moved;
        if (target == null) {
            moved = patrol();
        } else {
            moved = moveTo(target.x());
        }
        if (moved) {
            zone.service().monsterMove(this);
        }
        return moved;
    }

    private boolean moveTo(int targetX) {
        int step = movementStep();
        if (step <= 0 || targetX == x) {
            return false;
        }

        int direction = targetX > x ? 1 : -1;
        long distance = Math.abs((long) targetX - x);
        int actualStep = (int) Math.min(distance, step);
        x = Math.addExact(x, direction * actualStep);
        y = yFirst;
        moveDir = direction;
        return true;
    }

    private boolean patrol() {
        int step = movementStep();
        if (step <= 0) {
            return false;
        }

        int minX = Math.subtractExact(xFirst, template.rangeMove());
        int maxX = Math.addExact(xFirst, template.rangeMove());
        int beforeX = x;
        int beforeY = y;
        if (x < minX) {
            x = Math.min(minX, Math.addExact(x, step));
            moveDir = 1;
        } else if (x > maxX) {
            x = Math.max(maxX, Math.subtractExact(x, step));
            moveDir = -1;
        } else {
            if (x == maxX && moveDir > 0) {
                moveDir = -1;
            } else if (x == minX && moveDir < 0) {
                moveDir = 1;
            }

            long candidate = (long) x + (long) moveDir * step;
            if (candidate >= maxX) {
                x = maxX;
                moveDir = -1;
            } else if (candidate <= minX) {
                x = minX;
                moveDir = 1;
            } else {
                x = (int) candidate;
            }
        }
        y = yFirst;
        return x != beforeX || y != beforeY;
    }

    private int movementStep() {
        return Math.multiplyExact(template.speed(), MOVEMENT_STEP_MULTIPLIER);
    }

    private long squaredDistance(Player player) {
        long dx = (long) x - player.x();
        long dy = (long) y - player.y();
        try {
            return Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /** Đánh một kẻ thù trong tầm khi đã hết thời gian chờ. */
    boolean updateAttack(List<Player> hostilePlayers, long nowMillis, RandomGenerator random) {
        Objects.requireNonNull(hostilePlayers, "hostilePlayers");
        Objects.requireNonNull(random, "random");
        if (!isAlive()) {
            return false;
        }
        if (enemies.isEmpty()) {
            return false;
        }
        if (!attackDue(nowMillis)) {
            return false;
        }

        lastAttackAtMillis = nowMillis;
        Player target = findTarget(hostilePlayers, random);
        if (target == null) {
            return false;
        }
        target.injure(damage());
        zone.service().monsterAttack(this, target, damage());
        if (target.isDead()) {
            zone.forgetPlayer(target.id());
        }
        return true;
    }

    /** Finds a current living hostile Player inside the strict attack range. */
    Player findTarget(List<Player> hostilePlayers, RandomGenerator random) {
        int targetCount = 0;
        for (Player player : hostilePlayers) {
            if (player != null && isWithinAttackRange(player)) {
                targetCount++;
            }
        }
        if (targetCount == 0) {
            return null;
        }

        int targetIndex = random.nextInt(targetCount);
        for (Player player : hostilePlayers) {
            if (player == null || !isWithinAttackRange(player)) {
                continue;
            }
            if (targetIndex == 0) {
                return player;
            }
            targetIndex--;
        }
        return null;
    }

    private boolean isWithinAttackRange(Player player) {
        long dx = (long) x - player.x();
        long dy = (long) y - player.y();
        if (Math.abs(dx) >= ATTACK_RANGE || Math.abs(dy) >= ATTACK_RANGE) {
            return false;
        }
        return dx * dx + dy * dy < (long) ATTACK_RANGE * ATTACK_RANGE;
    }

    private boolean attackDue(long nowMillis) {
        return lastAttackAtMillis == 0L
                || nowMillis > deadlineAfter(lastAttackAtMillis, delayAttack());
    }

    private long delayAttack() {
        return Math.max(2_000L - 400L * enemies.size(), 500L);
    }

    private static long deadlineAfter(long start, long delay) {
        if (delay < 0L) {
            throw new IllegalArgumentException("delay must be non-negative");
        }
        return start > Long.MAX_VALUE - delay ? Long.MAX_VALUE : start + delay;
    }

    public boolean isAlive() {
        return status == STATUS_LIVE && hp > 0L;
    }

    public int id() {
        return id;
    }

    public long damage() {
        return template.damage();
    }

    public long hp() {
        return hp;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int levelStatus() {
        return levelStatus;
    }

    int rangeMove() {
        return template.rangeMove();
    }

    int speed() {
        return template.speed();
    }

    int moveType() {
        return template.type();
    }

    public int moveDir() {
        return moveDir;
    }

    public List<Integer> enemyPlayerIds() {
        return List.copyOf(enemies);
    }

    public boolean hasEnemy(int playerId) {
        return enemies.contains(playerId);
    }

    public boolean removeEnemy(int playerId) {
        return enemies.remove(playerId);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                type, template.id(), id, level, levelStatus, x, y, maxHp, hp, status);
    }

    /** Immutable copy of current state for MAP_INFO encoding. */
    public record Snapshot(
            int type,
            int templateId,
            int id,
            int level,
            int levelStatus,
            int x,
            int y,
            long maxHp,
            long hp,
            int status
    ) {
    }
}
