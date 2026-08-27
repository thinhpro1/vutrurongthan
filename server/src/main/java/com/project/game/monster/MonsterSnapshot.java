package com.project.game.monster;

public record MonsterSnapshot(
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
) {}
