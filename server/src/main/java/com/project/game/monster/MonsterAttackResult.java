package com.project.game.monster;

public record MonsterAttackResult(
        int monsterId,
        int playerId,
        long damage,
        long hpAfter
) {}
