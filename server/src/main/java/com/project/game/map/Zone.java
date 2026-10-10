package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.service.AreaService;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Một khu vực của Map (giống Zone của rongthan): trong khu vực có những ai, mỗi nhịp làm gì,
 * ai vào, ai ra.
 *
 * <p>Mọi việc trong khu vực chạy lần lượt trên thread riêng của Zone ({@link ZoneWriter}),
 * nên Player và Monster không cần khóa. Người chơi gửi lệnh vào bằng {@link #post}.
 */
public final class Zone {
    static final long UPDATE_PERIOD_MILLIS = 100L;

    private final Map map;
    private final int zoneId;
    private final AreaService service;
    private final ZoneWriter thread;

    // Trong khu vực có gì (sau này thêm: bosses, npcs, itemMaps)
    private final LinkedHashMap<Integer, Player> players = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    // Thread khác (MapManager chọn Zone) chỉ đọc con số này.
    private volatile int playerCount;

    public Zone(Map map, int zoneId, List<Monster> monsters, AreaService service) {
        this(map, zoneId, monsters, ZoneWriter.DEFAULT_INPUT_CAPACITY, service);
    }

    Zone(Map map, int zoneId, List<Monster> monsters, int inputCapacity, AreaService service) {
        this.map = Objects.requireNonNull(map, "map");
        this.zoneId = zoneId;
        this.service = Objects.requireNonNull(service, "service");
        this.thread = new ZoneWriter(map.id(), zoneId, inputCapacity);
        for (Monster monster : monsters) {
            addMonster(monster);
        }
    }

    public Map map() {
        return map;
    }

    public int mapId() {
        return map.id();
    }

    public int zoneId() {
        return zoneId;
    }

    public int maxPlayer() {
        return map.template().maxPlayer();
    }

    public AreaService service() {
        return service;
    }

    // ------------------------------------------------------------------
    // Mỗi nhịp (100 ms, chỉ khi còn người chơi)
    // ------------------------------------------------------------------

    private void update(long now, RandomGenerator random) {
        for (Player player : players.values()) {
            player.update(now);
        }
        for (Monster monster : monsters.values()) {
            monster.update(now, random);
        }
    }

    // ------------------------------------------------------------------
    // Người chơi vào / ra
    // ------------------------------------------------------------------

    /**
     * Player tới khu vực (đăng nhập hoặc đi từ map khác): thêm vào danh sách và gửi MAP_INFO.
     * Người khác chưa thấy Player cho tới khi client tải map xong ({@link #finishLoadMap}).
     */
    void enter(Player player) {
        if (isClosed(player)) {
            player.stopTravel(this); // thoát game giữa đường: không vào nữa
            return;
        }
        Player other = players.get(player.id());
        if (other != null && other != player) {
            player.stopTravel(this); // cùng nhân vật đang ở đây bằng kết nối khác
            kick(player);
            return;
        }
        players.put(player.id(), player);
        playerCount = players.size();
        player.enterZone(this);
        service.mapInfo(player);
        if (player.takeWakeUpFromDie()) {
            service.wakeUpFromDie(player);
        }
    }

    /** Client đã tải xong map: Player và những người trong khu vực thấy nhau. */
    public void finishLoadMap(Player player) {
        thread.submit(() -> {
            if (findPlayer(player.id()) != player) {
                return;
            }
            if (!player.isLoading()) {
                return;
            }
            player.finishLoadMap();
            service.addPlayer(this, player, players.values());
        });
    }

    /** Player rời khu vực (đi map khác hoặc thoát game): báo cho những người còn lại. */
    void leave(Player player) {
        if (players.remove(player.id(), player)) {
            playerCount = players.size();
            forgetPlayer(player.id());
            if (!player.isLoading()) {
                service.removePlayer(this, player);
            }
        }
        player.leaveZone(this);
    }

    // ------------------------------------------------------------------
    // Tìm / thêm / bớt
    // ------------------------------------------------------------------

    public Player findPlayer(int playerId) {
        return players.get(playerId);
    }

    public Monster findMonster(int monsterId) {
        return monsters.get(monsterId);
    }

    /** Những người đang ở trong khu vực (chỉ đọc). */
    public Collection<Player> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** Quái trong khu vực (chỉ đọc). */
    public Collection<Monster> monsters() {
        return Collections.unmodifiableCollection(monsters.values());
    }

    public int playerCount() {
        return playerCount;
    }

    /** Player có đang ở khu vực này không (đọc được từ thread khác). */
    public boolean hasPlayer(Player player) {
        return player != null && player.zone() == this;
    }

    /** Quái thôi thù Player đã chết hoặc đã rời khu vực. */
    public void forgetPlayer(int playerId) {
        for (Monster monster : monsters.values()) {
            monster.removeEnemy(playerId);
        }
    }

    /** Gửi packet cho Player thất bại (đầy hàng đợi): đóng kết nối của họ sau việc hiện tại. */
    public void kick(Player player) {
        Session session = player.session();
        if (session != null) {
            thread.later(session::close);
        }
    }

    private void addMonster(Monster monster) {
        Objects.requireNonNull(monster, "monster");
        if (monsters.putIfAbsent(monster.id(), monster) != null) {
            throw new IllegalArgumentException("duplicate monster runtime id " + monster.id()
                    + " in map " + mapId() + " zone " + zoneId);
        }
        monster.enterZone(this);
    }

    /** Có ai đã tải xong map chưa (chỉ khi đó khu vực mới cần chạy nhịp update). */
    private boolean hasPlayerInGame() {
        for (Player player : players.values()) {
            if (!player.isLoading()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClosed(Player player) {
        Session session = player.session();
        return session == null || session.state() == SessionState.CLOSED;
    }

    // ------------------------------------------------------------------
    // Gửi việc cho thread của Zone
    // ------------------------------------------------------------------

    /**
     * Lệnh của người chơi: xếp hàng, không chờ. Chỉ chạy nếu lúc đó Player vẫn ở đây và đã tải
     * xong map. Hàng đợi đầy thì bỏ lệnh (trả về false).
     */
    public boolean post(Player player, Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        return thread.submit(() -> {
            if (findPlayer(player.id()) != player) {
                return;
            }
            if (player.isLoading()) {
                return;
            }
            action.run();
        });
    }

    /** MapManager đưa Player tới đây; Player "đang đi" cho tới khi vào hẳn. */
    void postEnter(Player player) {
        player.startTravel(this);
        if (thread.submit(() -> enter(player))) {
            return;
        }
        // Hàng đợi đầy: không được làm rơi người chơi, nhưng cũng không được chờ trên thread
        // của Zone khác. Một virtual thread riêng chờ chỗ giúp.
        Thread.ofVirtual().name("zone-enter-" + mapId() + "-" + zoneId).start(() -> thread.call(() -> {
            enter(player);
            return null;
        }));
    }

    /**
     * Thoát game: rời khu vực nếu còn ở đây; chờ xong. Nếu Player đang đi tới đây thì việc
     * "vào" (xếp hàng trước) sẽ tự từ chối vì kết nối đã đóng và kết thúc chuyến đi.
     */
    void logout(Player player) {
        requireOutsideRuntimeWorker("logout");
        thread.call(() -> {
            if (findPlayer(player.id()) == player) {
                leave(player);
            }
            return null;
        });
    }

    /**
     * Bật vòng update trên thread của Zone (giống {@code Zone.run()} của rongthan): mỗi 100 ms
     * khi còn người chơi; khu vực trống thì nghỉ tới khi có việc mới.
     */
    public void startUpdate(Clock clock, RandomGenerator random) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(random, "random");
        thread.startUpdate(() -> update(clock.millis(), random), this::hasPlayerInGame,
                UPDATE_PERIOD_MILLIS);
    }

    /** Tắt vòng update và chờ nhịp đang chạy xong. */
    public void stopUpdate() {
        requireOutsideRuntimeWorker("stopUpdate");
        thread.stopUpdate();
    }

    /** Chạy một nhịp ngay và chờ xong (test, công cụ). */
    public void tick(long now, RandomGenerator random) {
        requireOutsideRuntimeWorker("tick");
        Objects.requireNonNull(random, "random");
        thread.call(() -> {
            update(now, random);
            return null;
        });
    }

    /** Code hạ tầng (Session, JDBC, Zone khác) không được chờ từ bên trong thread của một Zone. */
    public static void requireOutsideRuntimeWorker(String action) {
        ZoneWriter.requireOutsideWriter(action);
    }

    // Dùng cho MapManager và test trong package này.

    /** Như post nhưng chờ kết quả; false nếu Player không ở đây, đang tải map, hoặc hàng đợi đầy. */
    boolean run(Player player, BooleanSupplier action) {
        requireOutsideRuntimeWorker("run");
        try {
            return thread.tryCall(() -> findPlayer(player.id()) == player
                    && !player.isLoading()
                    && action.getAsBoolean());
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    <T> T call(Supplier<T> action) {
        return thread.call(action);
    }

    <T> T tryCall(Supplier<T> action) {
        return thread.tryCall(action);
    }

    boolean submit(Runnable action) {
        return thread.submit(action);
    }

    ZoneWriter.State runtimeState() {
        return thread.state();
    }

    void stopRuntime() {
        thread.stop();
    }
}
