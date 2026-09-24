package com.project.game.persistence.player;

import com.project.game.player.Appearance;
import com.project.game.player.BaseStats;
import com.project.game.player.CurrentStats;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;

import java.util.Objects;

/** Bản ghi Player bền vững, không chứa membership Zone chỉ dành cho runtime. */
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

    public static PlayerRecord withoutId(Player player) {
        Objects.requireNonNull(player, "player");
        return fromPlayer(player, 0);
    }

    public static PlayerRecord fromPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        return fromPlayer(player, player.id());
    }

    private static PlayerRecord fromPlayer(Player player, int id) {
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

    public static PlayerRecord fromSaveData(PlayerSaveData player) {
        Objects.requireNonNull(player, "player");
        return new PlayerRecord(
                player.id(),
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

    public Player toPlayer(int zoneId) {
        return new Player(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }
}
