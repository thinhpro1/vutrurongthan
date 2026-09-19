package com.project.game.player;

/** Durable snapshot of effective player statistics used by realtime combat. */
public record CurrentStats(
        long maxHp,
        long maxMp,
        long damage,
        long armor,
        int critical,
        int dodge,
        long constitution,
        int speed) {
    public CurrentStats {
        if (maxHp < 0L || maxMp < 0L || damage < 0L || armor < 0L
                || critical < 0 || dodge < 0 || constitution < 0L || speed <= 0) {
            throw new IllegalArgumentException("current stats must be non-negative and speed must be positive");
        }
    }
}
