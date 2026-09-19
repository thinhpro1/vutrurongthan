package com.project.game.player;

/** Durable snapshot of effective player statistics used by realtime combat. */
public record CurrentStats(
        int maxHp,
        int maxMp,
        int damage,
        int armor,
        int critical,
        int dodge,
        int constitution,
        int speed) {
    public CurrentStats {
        if (maxHp < 0 || maxMp < 0 || damage < 0 || armor < 0
                || critical < 0 || dodge < 0 || constitution < 0 || speed <= 0) {
            throw new IllegalArgumentException("current stats must be non-negative and speed must be positive");
        }
    }
}
