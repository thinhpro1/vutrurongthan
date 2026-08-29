package com.project.game.monster;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class RuntimeMonster {
    private static final int STATUS_LIVE = 0;
    private static final int STATUS_DIE = 1;
    private static final int MOVE_TYPE_RUN = 1;
    private static final int INITIAL_MOVE_DIR = 1;
    private static final int MOVEMENT_STEP_MULTIPLIER = 4;
    private static final long NO_RESPAWN = -1L;

    private final int id;
    private final int templateId;
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
    private final long damage;
    private final long potentialReward;
    private final int rangeMove;
    private final int speed;
    private final int moveType;
    private int moveDir = INITIAL_MOVE_DIR;
    private final LinkedHashMap<Integer, Long> enemies = new LinkedHashMap<>();
    private long lastAttackAtMillis;

    RuntimeMonster(LegacyMonsterSpawn spawn,
                   LegacyMonsterCombatTemplate combat,
                   LegacyMonsterTemplate movement) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(movement, "movement");
        if (combat.templateId() != spawn.templateId()) {
            throw new IllegalArgumentException("monster combat template does not match spawn");
        }
        if (combat.damage() <= 0L) {
            throw new IllegalArgumentException("monster combat damage must be positive");
        }
        if (movement.id() != spawn.templateId()) {
            throw new IllegalArgumentException("monster movement template does not match spawn");
        }
        if (movement.rangeMove() < 0) {
            throw new IllegalArgumentException("monster movement range must be non-negative");
        }
        if (movement.speed() < 0) {
            throw new IllegalArgumentException("monster movement speed must be non-negative");
        }
        id = spawn.id();
        templateId = spawn.templateId();
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
        damage = combat.damage();
        potentialReward = combat.potentialReward();
        rangeMove = movement.rangeMove();
        speed = movement.speed();
        moveType = movement.type();
    }

    public int id() {
        return id;
    }

    public boolean isAlive() {
        return status == STATUS_LIVE && hp > 0;
    }

    public long damage() {
        return damage;
    }

    int rangeMove() {
        return rangeMove;
    }

    int speed() {
        return speed;
    }

    int moveType() {
        return moveType;
    }

    int moveDir() {
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

    public long attackDelayMillis() {
        return Math.max(2_000L - 400L * enemies.size(), 500L);
    }

    public boolean beginAttackAttemptIfDue(long nowMillis) {
        if (!isAlive() || enemies.isEmpty()) {
            return false;
        }
        if (lastAttackAtMillis != 0L
                && nowMillis <= deadlineAfter(lastAttackAtMillis, attackDelayMillis())) {
            return false;
        }
        lastAttackAtMillis = nowMillis;
        return true;
    }

    public Optional<MonsterDamageResult> applyDamage(
            int attackerPlayerId,
            long damage,
            long nowMillis,
            long respawnDelayMillis) {
        if (damage <= 0 || !isAlive()) {
            return Optional.empty();
        }

        long hpAfter = Math.max(0L, hp - damage);
        boolean killed = hpAfter == 0L;
        long deadline = NO_RESPAWN;

        if (killed) {
            deadline = Math.addExact(nowMillis, respawnDelayMillis);
        }

        hp = hpAfter;

        enemies.merge(attackerPlayerId, damage, RuntimeMonster::saturatingAdd);

        if (killed) {
            status = STATUS_DIE;
            respawnAtMillis = deadline;
        }

        return Optional.of(new MonsterDamageResult(
                id,
                damage,
                hp,
                killed,
                killed ? potentialReward : 0L));
    }

    public Optional<MonsterRespawnResult> respawnIfDue(long nowMillis) {
        if (status != STATUS_DIE
                || respawnAtMillis == NO_RESPAWN
                || nowMillis <= respawnAtMillis) {
            return Optional.empty();
        }

        x = xFirst;
        y = yFirst;
        hp = maxHp;
        status = STATUS_LIVE;
        respawnAtMillis = NO_RESPAWN;
        enemies.clear();
        lastAttackAtMillis = 0L;
        moveDir = INITIAL_MOVE_DIR;

        return Optional.of(new MonsterRespawnResult(id, levelStatus, hp));
    }

    public Optional<MonsterMoveResult> moveToward(int targetX) {
        if (!isAlive() || moveType != MOVE_TYPE_RUN) {
            return Optional.empty();
        }

        int step = movementStep();
        if (step <= 0 || targetX == x) {
            return Optional.empty();
        }

        int direction = targetX > x ? 1 : -1;
        long distance = Math.abs((long) targetX - x);
        int actualStep = (int) Math.min(distance, step);

        x = Math.addExact(x, direction * actualStep);
        y = yFirst;
        moveDir = direction;

        return Optional.of(new MonsterMoveResult(id, x, y, moveDir));
    }

    public Optional<MonsterMoveResult> patrolOrReturn() {
        if (!isAlive() || moveType != MOVE_TYPE_RUN) {
            return Optional.empty();
        }

        int step = movementStep();
        if (step <= 0) {
            return Optional.empty();
        }

        int minX = Math.subtractExact(xFirst, rangeMove);
        int maxX = Math.addExact(xFirst, rangeMove);

        if (x < minX) {
            int target = Math.min(minX, Math.addExact(x, step));
            x = target;
            y = yFirst;
            moveDir = 1;
            return Optional.of(new MonsterMoveResult(id, x, y, moveDir));
        }

        if (x > maxX) {
            int target = Math.max(maxX, Math.subtractExact(x, step));
            x = target;
            y = yFirst;
            moveDir = -1;
            return Optional.of(new MonsterMoveResult(id, x, y, moveDir));
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
        y = yFirst;

        return Optional.of(new MonsterMoveResult(id, x, y, moveDir));
    }

    public MonsterSnapshot snapshot() {
        return new MonsterSnapshot(
                type,
                templateId,
                id,
                level,
                levelStatus,
                x,
                y,
                maxHp,
                hp,
                status);
    }

    private static long saturatingAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private int movementStep() {
        return Math.multiplyExact(speed, MOVEMENT_STEP_MULTIPLIER);
    }

    private static long deadlineAfter(long start, long delay) {
        if (delay < 0L) {
            throw new IllegalArgumentException("delay must be non-negative");
        }
        if (start > Long.MAX_VALUE - delay) {
            return Long.MAX_VALUE;
        }
        return start + delay;
    }
}
