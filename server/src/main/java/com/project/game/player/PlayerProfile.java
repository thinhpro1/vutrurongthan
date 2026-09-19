package com.project.game.player;

import java.util.Objects;
import java.util.regex.Pattern;

/** Runtime player state containing durable state plus a runtime-only zone id. */
public record PlayerProfile(
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
        long hp,
        long mp,
        Appearance appearance,
        long coin,
        long coinLock,
        int diamond,
        int ruby,
        int mapId,
        int zoneId,
        int x,
        int y) {
    private static final Pattern NAME = Pattern.compile("^[a-z0-9]{5,10}$");

    public PlayerProfile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseStats, "baseStats");
        Objects.requireNonNull(currentStats, "currentStats");
        Objects.requireNonNull(appearance, "appearance");
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        if (accountId <= 0L) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("name must match [a-z0-9]{5,10}");
        }
        if (gender < 0 || gender > 2) {
            throw new IllegalArgumentException("gender must be 0..2");
        }
        if (power < 0L || potential < 0L || level < 0 || exp < 0L
                || coin < 0L || coinLock < 0L || diamond < 0 || ruby < 0) {
            throw new IllegalArgumentException("durable player values must be non-negative");
        }
        if (hp < 0L || hp > currentStats.maxHp()) {
            throw new IllegalArgumentException("hp must be between 0 and current maxHp");
        }
        if (mp < 0L || mp > currentStats.maxMp()) {
            throw new IllegalArgumentException("mp must be between 0 and current maxMp");
        }
    }

    public PlayerProfile withPosition(int x, int y) {
        return withLocation(mapId, zoneId, x, y);
    }

    public PlayerProfile withHp(long hp) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }

    public PlayerProfile withMp(long mp) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }

    public PlayerProfile withPotential(long potential) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }

    public PlayerProfile withLocation(int mapId, int zoneId, int x, int y) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, hp, mp, appearance, coin, coinLock,
                diamond, ruby, mapId, zoneId, x, y);
    }

    public PlayerProfile revivedAt(int mapId, int zoneId, int x, int y) {
        return new PlayerProfile(
                id, accountId, name, gender, power, potential, level, exp,
                baseStats, currentStats, currentStats.maxHp(), currentStats.maxMp(),
                appearance, coin, coinLock, diamond, ruby,
                mapId, zoneId, x, y);
    }

    public static PlayerProfile initial(long accountId, int id, String name, int gender) {
        return new PlayerInitialProfileFactory().createWithId(id, accountId, name, gender);
    }
}
