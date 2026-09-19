package com.project.game.persistence.player;

import com.project.game.player.Appearance;
import com.project.game.player.BaseStats;
import com.project.game.player.CurrentStats;
import com.project.game.player.PlayerProfile;

import java.util.Objects;

/** Durable player row without runtime-only zone membership. */
public record PlayerRecord(
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
    public PlayerRecord {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseStats, "baseStats");
        Objects.requireNonNull(currentStats, "currentStats");
        Objects.requireNonNull(appearance, "appearance");
        if (id < 0 || accountId <= 0L || power < 0L || potential < 0L || level < 0
                || exp < 0L || coin < 0L || coinLock < 0L || diamond < 0 || ruby < 0) {
            throw new IllegalArgumentException("durable player values are invalid");
        }
        if (hp < 0L || hp > currentStats.maxHp()
                || mp < 0L || mp > currentStats.maxMp()) {
            throw new IllegalArgumentException("player vitals are outside current stat bounds");
        }
    }

    public static PlayerRecord withoutId(PlayerProfile player) {
        Objects.requireNonNull(player, "player");
        return fromProfile(player, 0);
    }

    public static PlayerRecord fromProfile(PlayerProfile player) {
        Objects.requireNonNull(player, "player");
        return fromProfile(player, player.id());
    }

    private static PlayerRecord fromProfile(PlayerProfile player, int id) {
        return new PlayerRecord(
                id,
                player.accountId(),
                player.name(),
                player.gender(),
                player.power(),
                player.potential(),
                player.level(),
                player.exp(),
                player.baseStats(),
                player.currentStats(),
                player.hp(),
                player.mp(),
                player.appearance(),
                player.coin(),
                player.coinLock(),
                player.diamond(),
                player.ruby(),
                player.mapId(),
                player.x(),
                player.y());
    }

    public PlayerProfile toProfile(int zoneId) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }
}
