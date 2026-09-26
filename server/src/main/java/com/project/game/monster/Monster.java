package com.project.game.monster;

import com.project.game.player.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Mutable Monster gameplay state. Zone owns when this behavior runs. */
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

    private int x;
    private int y;
    private long maxHp;
    private long hp;
    private int status;
    private long respawnAtMillis = NO_RESPAWN;
    private int moveDir = INITIAL_MOVE_DIR;
    private final LinkedHashMap<Integer, Long> enemies = new LinkedHashMap<>();
    private long lastAttackAtMillis;

    Monster(MonsterSpawn spawn, MonsterTemplate template) {
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

    /** Runs this Monster's own respawn, movement, and attack decisions. */
    public Attack update(
            List<Player> hostilePlayers, long nowMillis, RandomGenerator random) {
        Objects.requireNonNull(hostilePlayers, "hostilePlayers");
        Objects.requireNonNull(random, "random");
        if (!isAlive()) {
            updateRespawn(nowMillis);
            return null;
        }

        updateMove(hostilePlayers);
        return updateAttack(hostilePlayers, nowMillis, random);
    }

    public boolean isAlive() {
        return status == STATUS_LIVE && hp > 0L;
    }

    /** Applies Player damage and captures the respawn deadline on the lethal hit. */
    public Damage injure(int attackerPlayerId, long damage, long nowMillis, int playerCount) {
        if (damage <= 0L || !isAlive()) {
            return null;
        }
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must be non-negative");
        }

        long hpAfter = Math.max(0L, hp - damage);
        boolean killed = hpAfter == 0L;
        long respawnAt = NO_RESPAWN;
        if (killed) {
            respawnAt = Math.addExact(nowMillis, respawnDelayMillis(playerCount));
        }

        hp = hpAfter;
        enemies.merge(attackerPlayerId, damage, Monster::saturatingAdd);
        if (killed) {
            status = STATUS_DIE;
            respawnAtMillis = respawnAt;
        }

        return new Damage(
                id, damage, hp, killed, killed ? template.potentialReward() : 0L);
    }

    /** Restores a dead Monster only after its strict respawn deadline. */
    boolean updateRespawn(long nowMillis) {
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
        return true;
    }

    /** Chooses patrol or chase movement from the hostile Players supplied by its Zone. */
    boolean updateMove(List<Player> hostilePlayers) {
        Objects.requireNonNull(hostilePlayers, "hostilePlayers");
        if (!isAlive() || template.type() != MOVE_TYPE_RUN) {
            return false;
        }

        for (Player player : hostilePlayers) {
            if (player != null && isWithinAttackRange(player)) {
                return false;
            }
        }

        Player target = null;
        long targetDistance = Long.MAX_VALUE;
        for (Player player : hostilePlayers) {
            if (player == null || Math.abs((long) player.x() - xFirst) > CHASE_LEASH) {
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
        return target == null ? patrol() : moveTo(target.x());
    }

    /** Attacks one valid in-range hostile Player when cooldown is strictly due. */
    Attack updateAttack(
            List<Player> hostilePlayers, long nowMillis, RandomGenerator random) {
        Objects.requireNonNull(hostilePlayers, "hostilePlayers");
        Objects.requireNonNull(random, "random");
        if (!isAlive() || enemies.isEmpty() || !attackDue(nowMillis)) {
            return null;
        }

        lastAttackAtMillis = nowMillis;
        Player target = findTarget(hostilePlayers, random);
        if (target == null) {
            return null;
        }
        int hpAfter = target.injure(damage());
        return new Attack(id, target.id(), damage(), hpAfter, hpAfter == 0L);
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

    public int xFirst() {
        return xFirst;
    }

    public List<Integer> enemyPlayerIds() {
        return List.copyOf(enemies.keySet());
    }

    public int enemyCount() {
        return enemies.size();
    }

    public boolean hasEnemy(int playerId) {
        return enemies.containsKey(playerId);
    }

    public boolean removeEnemy(int playerId) {
        return enemies.remove(playerId) != null;
    }

    public long attackDelay() {
        return Math.max(2_000L - 400L * enemies.size(), 500L);
    }

    public MonsterSnapshot snapshot() {
        return new MonsterSnapshot(
                type, template.id(), id, level, levelStatus, x, y, maxHp, hp, status);
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

    private boolean attackDue(long nowMillis) {
        return lastAttackAtMillis == 0L
                || nowMillis > deadlineAfter(lastAttackAtMillis, attackDelay());
    }

    private boolean isWithinAttackRange(Player player) {
        long dx = (long) x - player.x();
        long dy = (long) y - player.y();
        if (Math.abs(dx) >= ATTACK_RANGE || Math.abs(dy) >= ATTACK_RANGE) {
            return false;
        }
        return dx * dx + dy * dy < (long) ATTACK_RANGE * ATTACK_RANGE;
    }

    private long squaredDistance(Player player) {
        long dx = (long) x - player.x();
        long dy = (long) y - player.y();
        return saturatingSquare(dx, dy);
    }

    private static long respawnDelayMillis(int playerCount) {
        return Math.max(10_000L - 1_000L * playerCount, 5_000L);
    }

    private static long saturatingAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingSquare(long first, long second) {
        try {
            return Math.addExact(Math.multiplyExact(first, first), Math.multiplyExact(second, second));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private int movementStep() {
        return Math.multiplyExact(template.speed(), MOVEMENT_STEP_MULTIPLIER);
    }

    private static long deadlineAfter(long start, long delay) {
        if (delay < 0L) {
            throw new IllegalArgumentException("delay must be non-negative");
        }
        return start > Long.MAX_VALUE - delay ? Long.MAX_VALUE : start + delay;
    }

    public record Damage(int monsterId, long damage, long hpAfter, boolean killed, long potentialReward) {
    }

    public record Move(int monsterId, int x, int y, int dir) {
    }

    public record Respawn(int monsterId, int levelStatus, long hp) {
    }

    public record Attack(int monsterId, int playerId, long damage, long hpAfter, boolean killed) {
    }
}
