package com.project.game.player;

/** Durable/progression source statistics for a player. */
public record BaseStats(
        long hp,
        long mp,
        long damage,
        long armor,
        int critical,
        int dodge,
        long constitution,
        int speed) {
    public BaseStats {
        if (hp < 0L || mp < 0L || damage < 0L || armor < 0L
                || critical < 0 || dodge < 0 || constitution < 0L || speed <= 0) {
            throw new IllegalArgumentException("base stats must be non-negative and speed must be positive");
        }
    }
}
