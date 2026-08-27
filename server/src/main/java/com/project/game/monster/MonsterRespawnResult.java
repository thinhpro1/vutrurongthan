package com.project.game.monster;

public record MonsterRespawnResult(
        int monsterId,
        int levelStatus,
        long hp
) {
}
