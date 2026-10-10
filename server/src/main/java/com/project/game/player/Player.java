package com.project.game.player;

import com.project.game.map.MapManager;
import com.project.game.map.Waypoint;
import com.project.game.map.Zone;
import com.project.game.monster.Monster;
import com.project.game.network.Session;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Nhân vật của người chơi: mọi hành động của người chơi bắt đầu ở đây.
 * Các method gameplay chạy trên writer của Zone (qua {@code zone.post}), nên không cần lock.
 */
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
    // mapId/x/y are logical location state and participate in persistence.
    // zoneId is the runtime Zone/handoff destination and is not persisted.
    // None of these fields proves realtime Zone membership.
    private int mapId;
    private int zoneId;
    private int x;
    private int y;

    // Runtime, không lưu DB. volatile vì Handler/MapManager đọc từ thread khác.
    private volatile Session session;
    private volatile Zone zone;
    private volatile Zone travelingTo; // Zone đang đi tới (đã rời Zone cũ, chưa vào Zone mới)
    private boolean loading;           // đã vào Zone nhưng client chưa tải xong map
    private boolean wakeUpFromDie;     // gửi WAKE_UP_FROM_DIE sau MAP_INFO ở Zone mới
    private Monster focus;             // Monster đã chọn bằng useSkill, chờ attack

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
        Appearance appearance;
        if (gender == 0) {
            appearance = new Appearance(5, 6, -1, -1, -1, -1, 0);
        } else if (gender == 1) {
            appearance = new Appearance(3, 7, -1, -1, -1, -1, 0);
        } else {
            appearance = new Appearance(4, 8, -1, -1, -1, -1, 0);
        }
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

    public Session session() {
        return session;
    }

    /** Session gắn Player lúc đăng nhập; Player dùng nó để nhận packet riêng. */
    public void bindSession(Session session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Zone Player đang ở; null khi chưa vào hoặc đang đi giữa hai Zone. */
    public Zone zone() {
        return zone;
    }

    /** Zone đang đi tới, null nếu không đi đâu. */
    public Zone travelingTo() {
        return travelingTo;
    }

    /** Chỉ Zone gọi: Player bắt đầu đi tới Zone này. */
    public void startTravel(Zone destination) {
        travelingTo = Objects.requireNonNull(destination, "destination");
    }

    /** Chỉ Zone gọi: chuyến đi tới Zone này kết thúc (đã vào, hoặc thoát game giữa đường). */
    public void stopTravel(Zone destination) {
        if (travelingTo == destination) {
            travelingTo = null;
        }
    }

    /** Chỉ Zone gọi, trên writer của nó: Player đã ở trong Zone, client đang tải map. */
    public void enterZone(Zone zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
        travelingTo = null;
        loading = true;
        focus = null;
    }

    /** Chỉ Zone gọi, trên writer của nó. */
    public void leaveZone(Zone expected) {
        if (zone == expected) {
            zone = null;
            focus = null;
        }
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean isLoading() {
        return loading;
    }

    /** FINISH_LOAD_MAP: client tải xong map, Player bắt đầu nhận lệnh. */
    public void finishLoadMap() {
        loading = false;
    }

    /** Mỗi nhịp của Zone. Chỗ thêm hồi HP/MP, hết hạn buff/effect sau này. */
    public void update(long now) {
    }

    /** Sát thương một đòn đánh thường. Sửa công thức dame ở đây. */
    public long damage() {
        return currentStats.damage();
    }

    // ------------------------------------------------------------------
    // Đi lại giữa các map
    // ------------------------------------------------------------------

    /** REQUEST_CHANGE_MAP: đứng trên waypoint thì sang map của waypoint đó. */
    public void requestChangeMap() {
        if (isDead()) {
            return;
        }
        Waypoint waypoint = zone.map().findWaypoint(x, y);
        if (waypoint == null) {
            return;
        }
        MapManager maps = zone.map().manager();
        maps.travel(this, waypoint.goMap(), waypoint.goX(), waypoint.goY());
    }

    /** RETURN_TOWN_FROM_DIE: đang chết thì hồi đầy máu và về nhà. */
    public void returnTownFromDead() {
        if (!isDead()) {
            return;
        }
        hp = currentStats.maxHp();
        mp = currentStats.maxMp();
        wakeUpFromDie = true;
        MapManager maps = zone.map().manager();
        maps.travel(this, MapManager.HOME_MAP_ID, MapManager.HOME_X, MapManager.HOME_Y);
    }

    /** Zone gọi sau khi gửi MAP_INFO: có cần báo hồi sinh không (chỉ một lần). */
    public boolean takeWakeUpFromDie() {
        boolean pending = wakeUpFromDie;
        wakeUpFromDie = false;
        return pending;
    }

    public boolean move(int x, int y) {
        if (isDead()) {
            return false;
        }
        this.x = x;
        this.y = y;
        zone.service().playerMove(this);
        return true;
    }

    public int injure(long damage) {
        if (damage < 0L) {
            throw new IllegalArgumentException("damage must be non-negative");
        }
        hp = (int) Math.max(0L, (long) hp - damage);
        return hp;
    }

    /** Packet -72: chọn skill và Monster làm mục tiêu cho lần attack kế tiếp. */
    public boolean useSkill(int skillId, int monsterId) {
        focus = null;
        if (monsterId < 0) {
            return false;
        }
        Monster monster = zone.findMonster(monsterId);
        if (!canTarget(monster)) {
            return false;
        }
        focus = monster;
        return true;
    }

    /** Packet -108: đánh Monster đã chọn ở useSkill. */
    public boolean attack(int monsterId, long nowMillis) {
        Monster target = focus;
        focus = null;
        if (target == null) {
            return false;
        }
        if (target.id() != monsterId) {
            return false;
        }
        return attackMonster(target, nowMillis);
    }

    public boolean canTarget(Monster monster) {
        if (isDead()) {
            return false;
        }
        if (monster == null) {
            return false;
        }
        return monster.isAlive();
    }

    /** Một đòn đánh vào Monster; Monster tự xử lý bị thương, chết và thưởng. */
    public boolean attackMonster(Monster monster, long nowMillis) {
        if (!canTarget(monster)) {
            return false;
        }
        long damage = damage();
        if (damage <= 0L) {
            return false;
        }
        monster.injure(this, damage, nowMillis);
        return true;
    }

    public long addPotential(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (potential > Long.MAX_VALUE - amount) {
            potential = Long.MAX_VALUE;
        } else {
            potential += amount;
        }
        return potential;
    }

    /** Changes logical location/handoff fields only; it does not join a Zone. */
    public void changeMap(int mapId, int zoneId, int x, int y) {
        focus = null;
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.x = x;
        this.y = y;
    }

    /** Restores vitals and changes logical location without world orchestration. */
    public void revive(int mapId, int zoneId, int x, int y) {
        hp = currentStats.maxHp();
        mp = currentStats.maxMp();
        changeMap(mapId, zoneId, x, y);
    }

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
}
