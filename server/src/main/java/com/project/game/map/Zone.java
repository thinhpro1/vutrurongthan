package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.monster.Monster.Snapshot;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Một khu vực của Map: giữ Player và Monster, quyết định KHI NÀO chúng được chạy.
 *
 * <p>Mọi hành động đi vào qua {@link #post}: Handler đọc packet rồi
 * {@code zone.post(player, () -> player.move(x, y))}. Zone chỉ kiểm tra Player còn ở đây rồi chạy
 * hành động trên writer (một virtual thread), nên Player/Monster không cần lock.
 * Phần cơ chế writer nằm ở cuối file và trong {@link ZoneWriter}.
 */
public final class Zone {
    static final long UPDATE_PERIOD_MILLIS = 100L;
    private static final Logger LOGGER = Logger.getLogger(Zone.class.getName());

    private final int mapId;
    private final int zoneId;
    private final int maxPlayer;
    private final AreaService service;
    private final LinkedHashMap<Integer, Player> players = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Player> reservedPlayers = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    private final ZoneWriter writer;
    // Người nhận bị đầy hàng đợi gửi; Session của họ được đóng sau khi rời writer.
    private final List<Player> kicked = new ArrayList<>();

    public Zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters, AreaService service) {
        this(mapId, zoneId, maxPlayer, monsters, ZoneWriter.DEFAULT_INPUT_CAPACITY, service);
    }

    Zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters,
         int inputCapacity, AreaService service) {
        if (maxPlayer <= 0) {
            throw new IllegalArgumentException("maxPlayer must be positive");
        }
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.maxPlayer = maxPlayer;
        this.writer = new ZoneWriter(mapId, zoneId, inputCapacity);
        this.service = Objects.requireNonNull(service, "service");
        Objects.requireNonNull(monsters, "monsters");
        for (Monster monster : monsters) {
            addMonster(Objects.requireNonNull(monster, "monster"));
        }
    }

    public int mapId() {
        return mapId;
    }

    public int zoneId() {
        return zoneId;
    }

    public int maxPlayer() {
        return maxPlayer;
    }

    public AreaService service() {
        return service;
    }

    // ------------------------------------------------------------------
    // Cửa vào duy nhất cho hành động của Player
    // ------------------------------------------------------------------

    /**
     * Xếp một hành động của Player vào hàng đợi, không chờ. Hành động chỉ chạy nếu lúc đó Player
     * vẫn ở Zone này; hàng đợi đầy thì bỏ (trả về false).
     */
    public boolean post(Player player, Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        return writer.submit(() -> runOnWriter(() -> {
            if (players.get(player.id()) == player) {
                action.run();
            }
        }));
    }

    /** Như {@link #post} nhưng chờ kết quả; dùng ở biên hạ tầng và test. */
    boolean run(Player player, BooleanSupplier action) {
        requireOutsideRuntimeWorker("run");
        try {
            return tryCall(() -> players.get(player.id()) == player && action.getAsBoolean());
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Player vào / ra Zone
    // ------------------------------------------------------------------

    /** Player vào Zone sau FINISH_LOAD_MAP và trao đổi hiện diện với người đang ở đây. */
    public boolean enter(Player player) {
        return enter(player, null);
    }

    /** {@code admitted} chạy trên writer ngay khi Player được nhận, trước khi gửi packet. */
    boolean enter(Player player, Runnable admitted) {
        requireOutsideRuntimeWorker("enter");
        if (!canEnter(player)) {
            return false;
        }
        try {
            return tryCall(() -> {
                if (!canEnter(player)) {
                    return false;
                }
                if (player.zone() != null) {
                    return player.zone() == this && hasPlayer(player);
                }
                JoinResult join = addPlayer(player);
                if (join.status() == JoinStatus.FULL) {
                    return false;
                }
                if (join.status() == JoinStatus.PLAYER_ID_CONFLICT) {
                    return false;
                }
                player.enterZone(this);
                if (join.status() == JoinStatus.ALREADY_PRESENT) {
                    return true;
                }
                if (admitted != null) {
                    admitted.run();
                }
                service.addPlayer(this, player, join.existing());
                return true;
            });
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Player rời Zone (thoát game); trả về bản lưu ổn định nếu đã tách hẳn khỏi Zone. */
    public PlayerSaveData leave(Player player) {
        requireOutsideRuntimeWorker("leave");
        if (player == null) {
            return null;
        }
        return call(() -> {
            removeFromZone(player);
            if (player.zone() != null) {
                return null;
            }
            return PlayerSaveData.capture(player);
        });
    }

    /** MapManager dọn Player khỏi Zone này mà không chụp (Player có thể đã thuộc Zone khác). */
    void drop(Player player) {
        requireOutsideRuntimeWorker("drop");
        call(() -> {
            removeFromZone(player);
            return null;
        });
    }

    private void removeFromZone(Player player) {
        cancelReservation(player);
        boolean removed = removePlayer(player);
        player.leaveZone(this);
        if (removed) {
            service.removePlayer(this, player);
        }
    }

    // ------------------------------------------------------------------
    // Tra cứu cho Player / Monster / AreaService (gọi trên writer)
    // ------------------------------------------------------------------

    public Player findPlayer(int playerId) {
        return players.get(playerId);
    }

    public Monster findMonster(int monsterId) {
        return monsters.get(monsterId);
    }

    public int playerCount() {
        return players.size();
    }

    /** Monster thôi thù Player đã chết hoặc đã rời Zone. */
    public void forgetPlayer(int playerId) {
        for (Monster monster : monsters.values()) {
            monster.removeEnemy(playerId);
        }
    }

    /** Gửi packet cho Player thất bại: đóng Session của họ sau khi rời writer. */
    public void kick(Player player) {
        if (!kicked.contains(player)) {
            kicked.add(player);
        }
    }

    // ------------------------------------------------------------------
    // Vòng update
    // ------------------------------------------------------------------

    /**
     * Bật vòng update của Zone trên virtual thread riêng (giống {@code Zone.run()} của rongthan):
     * mỗi 100 ms gọi update khi Zone còn Player, Zone trống thì nghỉ cho tới khi có input mới.
     */
    public void startUpdate(Clock clock, RandomGenerator random) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(random, "random");
        writer.startUpdate(() -> runOnWriter(() -> updateMonsters(clock.millis(), random)),
                () -> size() > 0, UPDATE_PERIOD_MILLIS);
    }

    /** Tắt vòng update và chờ nhịp đang chạy xong; Zone vẫn nhận hành động của Player. */
    public void stopUpdate() {
        requireOutsideRuntimeWorker("stopUpdate");
        writer.stopUpdate();
    }

    /** Một nhịp update chạy từ bên ngoài (test, công cụ); thường do vòng update tự gọi. */
    public void update(long nowMillis, RandomGenerator random) {
        requireOutsideRuntimeWorker("update");
        Objects.requireNonNull(random, "random");
        call(() -> {
            updateMonsters(nowMillis, random);
            return null;
        });
    }

    private void updateMonsters(long nowMillis, RandomGenerator random) {
        for (Monster monster : monsters.values()) {
            monster.update(nowMillis, random);
        }
    }

    /** Trạng thái Monster hiện tại để gửi MAP_INFO. */
    public synchronized List<Snapshot> monsterSnapshots() {
        List<Snapshot> snapshots = new ArrayList<>(monsters.size());
        for (Monster monster : monsters.values()) {
            snapshots.add(monster.snapshot());
        }
        return List.copyOf(snapshots);
    }

    private void addMonster(Monster monster) {
        if (monsters.putIfAbsent(monster.id(), monster) != null) {
            throw new IllegalArgumentException("duplicate monster runtime id " + monster.id()
                    + " in map " + mapId + " zone " + zoneId);
        }
        monster.enterZone(this);
    }

    // ------------------------------------------------------------------
    // Thành viên và đặt chỗ (MapManager dùng khi chuyển map)
    // ------------------------------------------------------------------

    /** Giữ một chỗ cho Player trước khi MapManager tách Player khỏi Zone nguồn. */
    ReserveStatus reserve(Player player) {
        requireOutsideRuntimeWorker("reserve");
        return call(() -> reservePlayer(player));
    }

    /** Trả lại chỗ đã giữ khi chuyển map không thành công. */
    boolean cancel(Player player) {
        requireOutsideRuntimeWorker("cancel");
        return call(() -> cancelReservation(player));
    }

    /** Thêm Player vào Zone (dùng chỗ đã giữ nếu có) và trả về những người đã ở trước. */
    synchronized JoinResult addPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        Player member = players.get(player.id());
        if (member == player) {
            return new JoinResult(JoinStatus.ALREADY_PRESENT, List.of());
        }
        if (member != null) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        Player reserved = reservedPlayers.get(player.id());
        if (reserved != null && reserved != player) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        if (reserved == player) {
            reservedPlayers.remove(player.id(), player);
        } else if (isFull()) {
            return new JoinResult(JoinStatus.FULL, List.of());
        }
        List<Player> existing = List.copyOf(players.values());
        players.put(player.id(), player);
        return new JoinResult(JoinStatus.ADDED, existing);
    }

    synchronized ReserveStatus reservePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        if (isClosed(player)) {
            return ReserveStatus.CLOSED;
        }
        Player member = players.get(player.id());
        if (member == player) {
            return ReserveStatus.ALREADY_PRESENT;
        }
        if (member != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }
        Player reserved = reservedPlayers.get(player.id());
        if (reserved == player) {
            return ReserveStatus.ALREADY_RESERVED;
        }
        if (reserved != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }
        if (isFull()) {
            return ReserveStatus.FULL;
        }
        reservedPlayers.put(player.id(), player);
        return ReserveStatus.RESERVED;
    }

    synchronized boolean cancelReservation(Player player) {
        if (player == null) {
            return false;
        }
        return reservedPlayers.remove(player.id(), player);
    }

    synchronized boolean hasReservation(Player player) {
        if (player == null) {
            return false;
        }
        return reservedPlayers.get(player.id()) == player;
    }

    synchronized int reservedCount() {
        return reservedPlayers.size();
    }

    synchronized boolean removePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        boolean removed = players.remove(player.id(), player);
        if (removed) {
            forgetPlayer(player.id());
        }
        return removed;
    }

    /** Bước cuối khi MapManager chuyển Player đi: gỡ backlink và báo cho người còn lại. */
    synchronized void detach(Player player) {
        if (!writer.isCurrent()) {
            throw new IllegalStateException("detach requires this Zone writer");
        }
        player.leaveZone(this);
        service.removePlayer(this, player);
    }

    public synchronized boolean hasPlayer(Player player) {
        if (player == null) {
            return false;
        }
        return players.get(player.id()) == player;
    }

    public synchronized int size() {
        return players.size();
    }

    /** Bản sao danh sách Player hiện tại. */
    public synchronized List<Player> players() {
        return List.copyOf(players.values());
    }

    private boolean isFull() {
        return players.size() + reservedPlayers.size() >= maxPlayer;
    }

    private boolean canEnter(Player player) {
        if (player == null) {
            return false;
        }
        if (isClosed(player)) {
            return false;
        }
        return player.mapId() == mapId && player.zoneId() == zoneId;
    }

    private static boolean isClosed(Player player) {
        Session session = player.session();
        return session == null || session.state() == SessionState.CLOSED;
    }

    // ------------------------------------------------------------------
    // Writer: mọi thay đổi gameplay của Zone chạy tuần tự trên một virtual thread
    // ------------------------------------------------------------------

    /** Code hạ tầng (Session, JDBC, Zone khác) không được chờ từ bên trong một Zone writer. */
    public static void requireOutsideRuntimeWorker(String action) {
        ZoneWriter.requireOutsideWriter(action);
    }

    /**
     * Chạy việc đã nằm trên writer (post, nhịp update): giữ monitor của Zone trong lúc chạy,
     * rồi giao các Session bị kick cho một virtual thread khác đóng, kể cả khi việc đó lỗi.
     */
    private void runOnWriter(Runnable action) {
        List<Player> toClose = new ArrayList<>();
        try {
            synchronized (this) {
                try {
                    action.run();
                } finally {
                    toClose.addAll(kicked);
                    kicked.clear();
                }
            }
        } finally {
            if (!toClose.isEmpty()) {
                Thread.ofVirtual().name("zone-kick-" + mapId + "-" + zoneId)
                        .start(() -> closeAll(toClose));
            }
        }
    }

    /** Hành động bắt buộc: chờ chỗ trống trong hàng đợi; ném lỗi nếu Zone đã dừng. */
    <T> T call(Supplier<T> action) {
        return execute(action, true);
    }

    /** Chờ kết quả nhưng từ chối ngay nếu hàng đợi đầy. */
    <T> T tryCall(Supplier<T> action) {
        return execute(action, false);
    }

    private <T> T execute(Supplier<T> action, boolean required) {
        if (writer.isCurrent()) {
            // Lời gọi lồng trên chính writer: lời gọi ngoài cùng sẽ đóng các Session bị kick.
            return action.get();
        }
        List<Player> toClose = new ArrayList<>();
        Supplier<T> onWriter = () -> {
            // synchronized để các truy vấn từ thread khác (hasPlayer, players...) thấy state nhất quán.
            synchronized (this) {
                try {
                    return action.get();
                } finally {
                    toClose.addAll(kicked);
                    kicked.clear();
                }
            }
        };
        try {
            if (required) {
                return writer.call(onWriter);
            }
            return writer.tryCall(onWriter);
        } finally {
            closeAll(toClose);
        }
    }

    /** Đóng Session của từng Player; lỗi của một người không bỏ qua những người còn lại. */
    private static void closeAll(List<Player> players) {
        for (Player player : players) {
            Session session = player.session();
            if (session == null) {
                continue;
            }
            try {
                session.close();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Kick close failed: session=" + session.id(), exception);
            }
        }
    }

    boolean submit(Runnable action) {
        return writer.submit(action);
    }

    ZoneWriter.State runtimeState() {
        return writer.state();
    }

    void stopRuntime() {
        writer.stop();
    }

    enum JoinStatus {
        ADDED,
        ALREADY_PRESENT,
        FULL,
        PLAYER_ID_CONFLICT
    }

    enum ReserveStatus {
        CLOSED,
        RESERVED,
        ALREADY_RESERVED,
        ALREADY_PRESENT,
        FULL,
        PLAYER_ID_CONFLICT
    }

    record JoinResult(JoinStatus status, List<Player> existing) {
        JoinResult {
            Objects.requireNonNull(status, "status");
            existing = List.copyOf(Objects.requireNonNull(existing, "existing"));
        }
    }
}
