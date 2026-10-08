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
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Một khu vực của Map: giữ Player, Monster và quyết định THỨ TỰ mọi thay đổi gameplay.
 *
 * <p>Mỗi hành động public có cùng một khuôn:
 * <pre>
 * public boolean move(session, x, y) {
 *     return tryRun(() -> {            // chạy trên writer (virtual thread) của Zone
 *         Player player = joinedPlayer(session);
 *         ...                          // Zone chọn lúc chạy, Player/Monster quyết định làm gì
 *         area.move(...)               // gửi packet; observer đầy hàng đợi bị kick
 *     });
 * }
 * </pre>
 * Cơ chế writer nằm ở cuối file và trong {@link ZoneWriter}; không cần đọc nó để hiểu gameplay.
 */
public final class Zone {
    static final long UPDATE_PERIOD_MILLIS = 100L;
    private final int mapId;
    private final int zoneId;
    private final int maxPlayer;
    private final AreaService area;
    private final LinkedHashMap<Integer, Session> members = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Session> reservedPlayers = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    private final ZoneWriter writer;
    // Observer gửi packet thất bại trong writer; được đóng sau khi rời writer.
    private final List<Session> kicked = new ArrayList<>();

    public Zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters, AreaService area) {
        this(mapId, zoneId, maxPlayer, monsters, ZoneWriter.DEFAULT_INPUT_CAPACITY, area);
    }

    Zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters,
         int inputCapacity, AreaService area) {
        if (maxPlayer <= 0) {
            throw new IllegalArgumentException("maxPlayer must be positive");
        }
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.maxPlayer = maxPlayer;
        this.writer = new ZoneWriter(mapId, zoneId, inputCapacity);
        this.area = Objects.requireNonNull(area, "area");
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

    // ------------------------------------------------------------------
    // Player vào / ra Zone
    // ------------------------------------------------------------------

    /** Player vào Zone sau FINISH_LOAD_MAP và trao đổi hiện diện với người đang ở đây. */
    public boolean enter(Session session) {
        return enter(session, null);
    }

    /** {@code admitted} chạy trên writer ngay khi Player được nhận, trước khi gửi packet. */
    boolean enter(Session session, Runnable admitted) {
        requireOutsideRuntimeWorker("enter");
        if (!canEnter(session)) {
            return false;
        }
        return tryRun(() -> {
            if (!canEnter(session)) {
                return false;
            }
            if (session.zone() != null) {
                return session.zone() == this && hasPlayer(session);
            }
            JoinResult join = addPlayer(session);
            if (join.status() == JoinStatus.FULL || join.status() == JoinStatus.PLAYER_ID_CONFLICT) {
                return false;
            }
            session.bindZone(this);
            if (join.status() == JoinStatus.ALREADY_PRESENT) {
                return true;
            }
            if (admitted != null) {
                admitted.run();
            }
            kick(area.addPlayer(session, session.player(), join.existing()));
            return true;
        });
    }

    /** Player rời Zone (disconnect); trả về bản lưu ổn định nếu đã tách hẳn khỏi Zone. */
    public PlayerSaveData leave(Session session) {
        requireOutsideRuntimeWorker("leave");
        if (session == null || session.player() == null) {
            return null;
        }
        return call(() -> {
            removeFromZone(session);
            return session.zone() == null ? PlayerSaveData.capture(session.player()) : null;
        });
    }

    /** MapManager dọn Session khỏi Zone này mà không chụp Player (có thể đã thuộc Zone khác). */
    void drop(Session session) {
        requireOutsideRuntimeWorker("drop");
        call(() -> {
            removeFromZone(session);
            return null;
        });
    }

    private void removeFromZone(Session session) {
        Player player = session.player();
        if (player == null) {
            return;
        }
        cancelReservation(session);
        boolean removed = removePlayer(session);
        session.clearZone(this);
        if (removed) {
            kick(area.removePlayer(session, player.id(), members()));
        }
    }

    // ------------------------------------------------------------------
    // Hành động của Player
    // ------------------------------------------------------------------

    /** Player di chuyển trong Zone rồi báo cho người khác. */
    public boolean move(Session session, int x, int y) {
        requireOutsideRuntimeWorker("move");
        return tryRun(() -> {
            Player player = joinedPlayer(session);
            if (player == null || player.isDead() || !player.move(x, y)) {
                return false;
            }
            kick(area.move(session, player, members()));
            return true;
        });
    }

    /** Player có thể nhắm Monster này không (dùng khi client chuẩn bị đòn đánh). */
    public boolean canTargetMonster(Session session, int monsterId) {
        requireOutsideRuntimeWorker("canTargetMonster");
        return tryRun(() -> {
            Player player = joinedPlayer(session);
            return player != null && player.canTarget(monsters.get(monsterId));
        });
    }

    /** Player đánh Monster; damage, chết và thưởng tiềm năng đều do Player/Monster quyết định. */
    public boolean attackMonster(Session session, int monsterId, long nowMillis) {
        requireOutsideRuntimeWorker("attackMonster");
        return tryRun(() -> {
            Player player = joinedPlayer(session);
            Monster monster = monsters.get(monsterId);
            if (player == null || monster == null) {
                return false;
            }
            Monster.Damage damage = player.attackMonster(monster, nowMillis, members.size());
            if (damage == null) {
                return false;
            }
            kick(area.monsterDamage(damage, members()));
            if (damage.killed() && damage.potentialReward() > 0L
                    && session.state() != SessionState.CLOSED
                    && !area.potential(session, player.potential())) {
                kicked.add(session);
            }
            return true;
        });
    }

    // ------------------------------------------------------------------
    // Monster
    // ------------------------------------------------------------------

    /**
     * Bật vòng update của Zone trên virtual thread riêng (giống {@code Zone.run()} của rongthan):
     * mỗi 100 ms gọi update khi Zone còn Player, Zone trống thì nghỉ cho tới khi có input mới.
     */
    public void startUpdate(Clock clock, RandomGenerator random) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(random, "random");
        writer.startUpdate(() -> updateOnWriter(clock.millis(), random),
                () -> size() > 0, UPDATE_PERIOD_MILLIS);
    }

    /** Tắt vòng update; Zone vẫn nhận hành động của Player. */
    public void stopUpdate() {
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

    /** Nhịp update do chính writer gọi: không được đóng Session tại đây nên giao cho thread khác. */
    private void updateOnWriter(long nowMillis, RandomGenerator random) {
        List<Session> toClose;
        synchronized (this) {
            try {
                updateMonsters(nowMillis, random);
            } finally {
                toClose = List.copyOf(kicked);
                kicked.clear();
            }
        }
        if (!toClose.isEmpty()) {
            Thread.ofVirtual().name("zone-kick-" + mapId + "-" + zoneId)
                    .start(() -> closeAll(toClose));
        }
    }

    /** Mỗi Monster tự update; Zone chỉ báo kết quả cho khu vực. */
    private void updateMonsters(long nowMillis, RandomGenerator random) {
        for (Monster monster : monsters.values()) {
            updateMonster(monster, nowMillis, random);
        }
    }

    private void updateMonster(Monster monster, long nowMillis, RandomGenerator random) {
        boolean wasAlive = monster.isAlive();
        int oldX = monster.x();
        int oldY = monster.y();
        Monster.Attack attack = monster.update(livingEnemies(monster), nowMillis, random);

        if (!wasAlive && monster.isAlive()) {
            kick(area.monsterRespawn(
                    new Monster.Respawn(monster.id(), monster.levelStatus(), monster.hp()), members()));
        } else if (oldX != monster.x() || oldY != monster.y()) {
            kick(area.monsterMove(
                    new Monster.Move(monster.id(), monster.x(), monster.y(), monster.moveDir()), members()));
        }

        if (attack == null) {
            return;
        }
        if (attack.killed()) {
            forgetPlayer(attack.playerId());
        }
        kick(area.monsterAttack(attack, members()));
    }

    /** Các Player còn sống trong Zone mà Monster đang thù. */
    private List<Player> livingEnemies(Monster monster) {
        List<Player> players = new ArrayList<>();
        for (int playerId : monster.enemyPlayerIds()) {
            Session member = members.get(playerId);
            if (member == null || member.state() == SessionState.CLOSED) {
                continue;
            }
            Player player = member.player();
            if (player != null && !player.isDead()) {
                players.add(player);
            }
        }
        return List.copyOf(players);
    }

    /** Monster không còn thù Player đã chết hoặc đã rời Zone. */
    private void forgetPlayer(int playerId) {
        for (Monster monster : monsters.values()) {
            monster.removeEnemy(playerId);
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
    }

    // ------------------------------------------------------------------
    // Thành viên và đặt chỗ (MapManager dùng khi chuyển map)
    // ------------------------------------------------------------------

    /** Giữ một chỗ cho Session trước khi MapManager tách Player khỏi Zone nguồn. */
    ReserveStatus reserve(Session session) {
        requireOutsideRuntimeWorker("reserve");
        return call(() -> reservePlayer(session));
    }

    /** Trả lại chỗ đã giữ khi chuyển map không thành công. */
    boolean cancel(Session session) {
        requireOutsideRuntimeWorker("cancel");
        return call(() -> cancelReservation(session));
    }

    /** Thêm Session vào members (dùng chỗ đã giữ nếu có) và trả về những người đã ở trước. */
    synchronized JoinResult addPlayer(Session session) {
        Objects.requireNonNull(session, "session");
        Player player = requirePlayer(session);
        Session member = members.get(player.id());
        if (member == session) {
            return new JoinResult(JoinStatus.ALREADY_PRESENT, List.of());
        }
        if (member != null) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        Session reserved = reservedPlayers.get(player.id());
        if (reserved != null && reserved != session) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        if (reserved == session) {
            reservedPlayers.remove(player.id(), session);
        } else if (isFull()) {
            return new JoinResult(JoinStatus.FULL, List.of());
        }
        List<Session> existing = List.copyOf(members.values());
        members.put(player.id(), session);
        return new JoinResult(JoinStatus.ADDED, existing);
    }

    synchronized ReserveStatus reservePlayer(Session session) {
        Objects.requireNonNull(session, "session");
        if (session.state() == SessionState.CLOSED) {
            return ReserveStatus.CLOSED;
        }
        Player player = requirePlayer(session);
        Session member = members.get(player.id());
        if (member == session) {
            return ReserveStatus.ALREADY_PRESENT;
        }
        if (member != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }
        Session reserved = reservedPlayers.get(player.id());
        if (reserved == session) {
            return ReserveStatus.ALREADY_RESERVED;
        }
        if (reserved != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }
        if (isFull()) {
            return ReserveStatus.FULL;
        }
        reservedPlayers.put(player.id(), session);
        return ReserveStatus.RESERVED;
    }

    synchronized boolean cancelReservation(Session session) {
        if (session == null || session.player() == null) {
            return false;
        }
        return reservedPlayers.remove(session.player().id(), session);
    }

    synchronized boolean hasReservation(Session session) {
        if (session == null || session.player() == null) {
            return false;
        }
        return reservedPlayers.get(session.player().id()) == session;
    }

    synchronized int reservedCount() {
        return reservedPlayers.size();
    }

    synchronized boolean removePlayer(Session session) {
        Objects.requireNonNull(session, "session");
        Player player = requirePlayer(session);
        boolean removed = members.remove(player.id(), session);
        if (removed) {
            forgetPlayer(player.id());
        }
        return removed;
    }

    /** Bước cuối khi MapManager chuyển Player đi: gỡ backlink và báo cho người còn lại. */
    synchronized void detach(Session session) {
        if (!writer.isCurrent()) {
            throw new IllegalStateException("detach requires this Zone writer");
        }
        session.clearZone(this);
        kick(area.removePlayer(session, session.player().id(), members()));
    }

    public synchronized boolean hasPlayer(Session session) {
        if (session == null || session.player() == null) {
            return false;
        }
        return members.get(session.player().id()) == session;
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized List<Session> members() {
        return List.copyOf(members.values());
    }

    private boolean isFull() {
        return members.size() + reservedPlayers.size() >= maxPlayer;
    }

    /** Player của Session nếu Session đang thật sự ở trong Zone này, ngược lại null. */
    private Player joinedPlayer(Session session) {
        if (session == null || session.state() == SessionState.CLOSED || session.zone() != this) {
            return null;
        }
        Player player = session.player();
        if (player == null || members.get(player.id()) != session) {
            return null;
        }
        return player;
    }

    private boolean canEnter(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        Player player = session.player();
        return player != null && player.mapId() == mapId && player.zoneId() == zoneId;
    }

    private static Player requirePlayer(Session session) {
        Player player = session.player();
        if (player == null) {
            throw new IllegalStateException("zone membership requires a bound player");
        }
        return player;
    }

    // ------------------------------------------------------------------
    // Writer: mọi thay đổi gameplay của Zone chạy tuần tự trên một virtual thread
    // ------------------------------------------------------------------

    /** Code hạ tầng (Session, JDBC, Zone khác) không được chờ từ bên trong một Zone writer. */
    public static void requireOutsideRuntimeWorker(String action) {
        ZoneWriter.requireOutsideWriter(action);
    }

    /** Đánh dấu observer gửi thất bại; Session được đóng sau khi rời writer. */
    private void kick(List<Session> sessions) {
        kicked.addAll(sessions);
    }

    /** Chạy hành động trên writer; hàng đợi đầy hoặc Zone đã dừng thì coi như không làm gì. */
    private boolean tryRun(Supplier<Boolean> action) {
        try {
            return tryCall(action);
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Hành động bắt buộc: chờ chỗ trống trong hàng đợi; ném lỗi nếu Zone đã dừng. */
    <T> T call(Supplier<T> action) {
        return execute(action, true);
    }

    <T> T tryCall(Supplier<T> action) {
        return execute(action, false);
    }

    private <T> T execute(Supplier<T> action, boolean required) {
        if (writer.isCurrent()) {
            // Lời gọi lồng trên chính writer: lời gọi ngoài cùng sẽ đóng các Session bị kick.
            return action.get();
        }
        List<Session> toClose = new ArrayList<>();
        Supplier<T> onWriter = () -> {
            // synchronized để các truy vấn từ thread khác (hasPlayer, members...) thấy state nhất quán.
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
            return required ? writer.call(onWriter) : writer.tryCall(onWriter);
        } finally {
            closeAll(toClose);
        }
    }

    private static void closeAll(List<Session> sessions) {
        for (Session session : sessions) {
            session.close();
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

    record JoinResult(JoinStatus status, List<Session> existing) {
        JoinResult {
            Objects.requireNonNull(status, "status");
            existing = List.copyOf(Objects.requireNonNull(existing, "existing"));
        }
    }
}
