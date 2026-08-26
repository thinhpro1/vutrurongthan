package com.project.game.monster;

import java.util.List;

/** Immutable client-visible animation phase for a monster dart. */
public record LegacyMonsterDartPhase(
        List<Integer> icons,
        int dx,
        int dy,
        int delay
) {
    public LegacyMonsterDartPhase {
        icons = List.copyOf(icons);
    }
}
