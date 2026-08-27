package com.project.game.monster;

import java.util.Objects;
import java.util.Optional;

public final class RuntimeMonster {
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
        return status == 0 && hp > 0;
    }

    public Optional<MonsterDamageResult> applyDamage(long damage) {
        if (damage <= 0 || !isAlive()) {
            return Optional.empty();
        }

        hp = Math.max(0L, hp - damage);
        boolean killed = hp == 0;

        if (killed) {
            status = 1;
        }

        return Optional.of(new MonsterDamageResult(id, damage, hp, killed));
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
