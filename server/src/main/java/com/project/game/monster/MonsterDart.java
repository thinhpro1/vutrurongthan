package com.project.game.monster;

import java.util.List;

/** Immutable client-visible monster dart template. */
public record MonsterDart(
        int id,
        boolean meteorite,
        Phase light,
        Phase bullet,
        Phase explode
) {
    /** Immutable client-visible animation phase for a monster dart. */
    public record Phase(
            List<Integer> icons,
            int dx,
            int dy,
            int delay
    ) {
        public Phase {
            icons = List.copyOf(icons);
        }
    }
}