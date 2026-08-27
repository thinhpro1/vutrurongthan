package com.project.game.monster;

public record MonsterDamageResult(
        int monsterId,
        long damage,
        long hpAfter,
        boolean killed
) {}
