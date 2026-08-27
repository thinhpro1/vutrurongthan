package com.project.game.monster;

import java.util.Objects;

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
