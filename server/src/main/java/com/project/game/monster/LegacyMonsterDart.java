package com.project.game.monster;

/** Immutable client-visible monster dart template. */
public record LegacyMonsterDart(
        int id,
        boolean meteorite,
        LegacyMonsterDartPhase light,
        LegacyMonsterDartPhase bullet,
        LegacyMonsterDartPhase explode
) {}
