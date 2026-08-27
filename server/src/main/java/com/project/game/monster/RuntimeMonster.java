package com.project.game.monster;

import java.util.Objects;
import java.util.Optional;

public final class RuntimeMonster {
    private static final int STATUS_LIVE = 0;
    private static final int STATUS_DIE = 1;
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

    RuntimeMonster(LegacyMonsterSpawn spawn) {
        Objects.requireNonNull(spawn, "spawn");
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
    }

    public int id() {
        return id;
    }

    public boolean isAlive() {
        return status == STATUS_LIVE && hp > 0;
    }

    public Optional<MonsterDamageResult> applyDamage(
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

        if (killed) {
            status = STATUS_DIE;
            respawnAtMillis = deadline;
        }

        return Optional.of(new MonsterDamageResult(id, damage, hp, killed));
    }

    public Optional<MonsterDamageResult> applyDamage(long damage) {
        if (damage <= 0 || !isAlive()) {
            return Optional.empty();
        }

        hp = Math.max(0L, hp - damage);
        boolean killed = hp == 0L;
        if (killed) {
            status = STATUS_DIE;
        }
        return Optional.of(new MonsterDamageResult(id, damage, hp, killed));
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

        return Optional.of(new MonsterRespawnResult(id, levelStatus, hp));
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
}
