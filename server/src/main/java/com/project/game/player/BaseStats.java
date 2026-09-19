package com.project.game.player;

/** Durable/progression source statistics for a player. */
public record BaseStats(
        int hp,
        int mp,
        int damage,
        int armor,
        int critical,
        int dodge,
        int constitution,
        int speed) {
    public BaseStats {
        if (hp < 0 || mp < 0 || damage < 0 || armor < 0
                || critical < 0 || dodge < 0 || constitution < 0 || speed <= 0) {
            throw new IllegalArgumentException("base stats must be non-negative and speed must be positive");
        }
    }
}
