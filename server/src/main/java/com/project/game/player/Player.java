package com.project.game.player;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Runtime Player mutable; Zone quyết định thời điểm gọi các chuyển trạng thái này. */
public final class Player {
    private static final Pattern NAME = Pattern.compile("^[a-z0-9]{5,10}$");

    private final int id;
    private final long accountId;
    private final String name;
    private final int gender;
    private long power;
    private long potential;
    private int level;
    private long exp;
    private final BaseStats baseStats;
    private final CurrentStats currentStats;
    private int hp;
    private int mp;
    private final Appearance appearance;
    private long coin;
    private long coinLock;
    private int diamond;
    private int ruby;
    private int mapId;
    private int zoneId;
    private int x;
    private int y;

    public static Player create(long accountId, String name, int gender) {
        return createWithId(0, accountId, name, gender);
    }

    public static Player createWithId(int id, long accountId, String name, int gender) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        String normalized = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        if (!NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("player name must match [a-z0-9]{5,10}");
        }
        if (gender < 0 || gender > 2) {
            throw new IllegalArgumentException("gender must be 0..2");
        }
        BaseStats base = new BaseStats(200, 200, 10, 0, 0, 0, 5, 12);
        CurrentStats current = new CurrentStats(
                base.hp(), base.mp(), base.damage(), base.armor(), base.critical(),
                base.dodge(), base.constitution(), base.speed());
        Appearance appearance = switch (gender) {
            case 0 -> new Appearance(5, 6, -1, -1, -1, -1, 0);
            case 1 -> new Appearance(3, 7, -1, -1, -1, -1, 0);
            case 2 -> new Appearance(4, 8, -1, -1, -1, -1, 0);
            default -> throw new IllegalStateException("gender was validated");
        };
        return new Player(
                id, accountId, normalized, gender, 1L, 1L, 1, 0L,
                base, current, current.maxHp(), current.maxMp(), appearance,
                0L, 10_000L, 0, 25, 0, 0, 1250, 648);
    }

    /** Dùng khi nạp một trạng thái đã được kiểm tra từ persistence. */
    public Player(
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
            int zoneId,
            int x,
            int y) {
        this.id = id;
        this.accountId = accountId;
        this.name = Objects.requireNonNull(name, "name");
        this.gender = gender;
        this.power = power;
        this.potential = potential;
        this.level = level;
        this.exp = exp;
        this.baseStats = Objects.requireNonNull(baseStats, "baseStats");
        this.currentStats = Objects.requireNonNull(currentStats, "currentStats");
        this.hp = hp;
        this.mp = mp;
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.coin = coin;
        this.coinLock = coinLock;
        this.diamond = diamond;
        this.ruby = ruby;
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.x = x;
        this.y = y;
        validate();
    }

    private void validate() {
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
        if (hp < 0 || hp > currentStats.maxHp()) {
            throw new IllegalArgumentException("hp must be between 0 and current maxHp");
        }
        if (mp < 0 || mp > currentStats.maxMp()) {
            throw new IllegalArgumentException("mp must be between 0 and current maxMp");
        }
    }

    public int id() { return id; }
    public long accountId() { return accountId; }
    public String name() { return name; }
    public int gender() { return gender; }
    public long power() { return power; }
    public long potential() { return potential; }
    public int level() { return level; }
    public long exp() { return exp; }
    public BaseStats baseStats() { return baseStats; }
    public CurrentStats currentStats() { return currentStats; }
    public int hp() { return hp; }
    public int mp() { return mp; }
    public Appearance appearance() { return appearance; }
    public long coin() { return coin; }
    public long coinLock() { return coinLock; }
    public int diamond() { return diamond; }
    public int ruby() { return ruby; }
    public int mapId() { return mapId; }
    public int zoneId() { return zoneId; }
    public int x() { return x; }
    public int y() { return y; }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean move(int x, int y) {
        if (isDead()) {
            return false;
        }
        this.x = x;
        this.y = y;
        return true;
    }

    public int injure(long damage) {
        if (damage < 0L) {
            throw new IllegalArgumentException("damage must be non-negative");
        }
        hp = (int) Math.max(0L, (long) hp - damage);
        return hp;
    }

    public long addPotential(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        potential = potential > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE
                : potential + amount;
        return potential;
    }

    public void changeMap(int mapId, int zoneId, int x, int y) {
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.x = x;
        this.y = y;
    }

    public void revive(int mapId, int zoneId, int x, int y) {
        hp = currentStats.maxHp();
        mp = currentStats.maxMp();
        changeMap(mapId, zoneId, x, y);
    }
}
