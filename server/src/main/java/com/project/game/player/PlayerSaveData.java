package com.project.game.player;

import java.util.Objects;

/**
 * Immutable Player data at the checkpoint boundary.
 * mapId and x/y are persisted; zoneId is runtime-only and is intentionally absent.
 */
public record PlayerSaveData(
        int id,
        long accountId,
        String name,
        int gender,
        long power,
        long potential,
        int level,
        long exp,
        BaseStats baseStats,
        CurrentStats currentStats,
        int hp,
        int mp,
        Appearance appearance,
        long coin,
        long coinLock,
        int diamond,
        int ruby,
        int mapId,
        int x,
        int y) {
    public PlayerSaveData {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseStats, "baseStats");
        Objects.requireNonNull(currentStats, "currentStats");
        Objects.requireNonNull(appearance, "appearance");
    }

    public static PlayerSaveData capture(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerSaveData(
                player.id(), player.accountId(), player.name(), player.gender(),
                player.power(), player.potential(), player.level(), player.exp(),
                player.baseStats(), player.currentStats(), player.hp(), player.mp(),
                player.appearance(), player.coin(), player.coinLock(), player.diamond(),
                player.ruby(), player.mapId(), player.x(), player.y());
    }
}
