package com.project.game.monster;

public record MonsterAttack(
        int monsterId,
        int playerId,
        long damage,
        long hpAfter,
        boolean killed
) {}
