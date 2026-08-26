package com.project.game.monster;

/** Immutable static monster instance sent in MAP_INFO. */
public record LegacyMonsterSpawn(
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
