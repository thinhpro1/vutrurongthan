package com.project.game.player;

/** Durable appearance identifiers; negative values are legacy empty sentinels. */
public record Appearance(
        int head,
        int body,
        int mount,
        int bag,
        int medal,
        int aura,
        int spaceship) {
    public Appearance {
        if (head < 0 || body < 0) {
            throw new IllegalArgumentException("head and body must be non-negative");
        }
    }
}
