package com.project.game.map;

import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runtime của một khu vực bản đồ: writer, membership và entity state. */
public final class Zone {
    private static final int DEFAULT_RUNTIME_INPUT_CAPACITY = 1024;
    private static final Logger LOGGER = Logger.getLogger(Zone.class.getName());
    private static final ThreadLocal<Zone> CURRENT_RUNTIME = new ThreadLocal<>();
    private final int mapId;
    private final int zoneId;
    private final int maxPlayer;
    private final AreaService area;
    private final ConcurrentHashMap<Integer, Session> members = new ConcurrentHashMap<>();
    private final LinkedHashMap<Integer, Session> reservedPlayers = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    private final ArrayBlockingQueue<Runnable> runtimeInputs;
    private final Object runtimeLock = new Object();
    private RuntimeState runtimeState = RuntimeState.FROZEN;
    private Thread runtimeWorker;

    public Zone(
            int mapId,
            int zoneId,
            int maxPlayer,
            List<Monster> monsters,
            AreaService area) {
        this(mapId, zoneId, maxPlayer, monsters, DEFAULT_RUNTIME_INPUT_CAPACITY, area);
    }

    Zone(
            int mapId,
            int zoneId,
            int maxPlayer,
            List<Monster> monsters,
            int inputCapacity,
            AreaService area) {
        if (maxPlayer <= 0) {
            throw new IllegalArgumentException("maxPlayer must be positive");
        }
        if (inputCapacity <= 0) {
            throw new IllegalArgumentException("inputCapacity must be positive");
        }
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.maxPlayer = maxPlayer;
        this.area = Objects.requireNonNull(area, "area");
        this.runtimeInputs = new ArrayBlockingQueue<>(inputCapacity);
        Objects.requireNonNull(monsters, "monsters");
        for (Monster monster : monsters) {
            Objects.requireNonNull(monster, "monster");
            if (this.monsters.putIfAbsent(monster.id(), monster) != null) {
                throw new IllegalArgumentException(
                        "duplicate monster runtime id "
                                + monster.id()
                                + " in map "
                                + mapId
                                + " zone "
                                + zoneId);
            }
        }
    }

    /**
     * Xếp một tác vụ vào writer runtime tuần tự của Zone. Việc thêm tác vụ cố ý
     * không chặn; bên gọi phải tự xử lý khi hàng đợi đầu vào đã đầy.
     */
    public boolean submit(Runnable action) {
        Objects.requireNonNull(action, "action");
        synchronized (runtimeLock) {
            if (runtimeState == RuntimeState.STOPPED || !runtimeInputs.offer(action)) {
                return false;
            }
            startRuntimeWorkerIfNeededLocked();
            return true;
        }
    }

    /** Chạy một tác vụ trên writer của Zone và từ chối ngay khi hàng đợi giới hạn đã đầy. */
    public <T> T tryCall(Supplier<T> action) {
        return executeCall(action, false);
    }

    /** Chạy một tác vụ bắt buộc trên writer của Zone, chờ chỗ trống nếu hàng đợi đã đầy. */
    public <T> T call(Supplier<T> action) {
        return executeCall(action, true);
    }

    private <T> T executeCall(Supplier<T> action, boolean required) {
        Objects.requireNonNull(action, "action");
        boolean onRuntimeWorker;
        synchronized (runtimeLock) {
            if (runtimeState == RuntimeState.STOPPED) {
                throw new RejectedExecutionException(
                        "Zone runtime is stopped for map " + mapId + " zone " + zoneId);
            }
            onRuntimeWorker = runtimeWorker == Thread.currentThread();
        }
        if (onRuntimeWorker) {
            return action.get();
        }

        Call<T> call = new Call<>(action, mapId, zoneId);
        if (required) {
            boolean interruptedWhileAdmitting = submitRequired(call);
            return call.await(interruptedWhileAdmitting);
        }
        if (!submit(call)) {
            throw new RejectedExecutionException(
                    "Zone runtime rejected action for map " + mapId + " zone " + zoneId);
        }
        return call.await(false);
    }

    private boolean submitRequired(Call<?> action) {
        boolean interrupted = false;
        synchronized (runtimeLock) {
            while (runtimeState != RuntimeState.STOPPED && !runtimeInputs.offer(action)) {
                try {
                    runtimeLock.wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (runtimeState == RuntimeState.STOPPED) {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                throw new RejectedExecutionException(
                        "Zone runtime is stopped for map " + mapId + " zone " + zoneId);
            }
            startRuntimeWorkerIfNeededLocked();
        }
        return interrupted;
    }

    RuntimeState runtimeState() {
        synchronized (runtimeLock) {
            return runtimeState;
        }
    }

    /**
     * Dừng vĩnh viễn runtime của Zone. Tác vụ đang chạy được phép hoàn tất;
     * các lời gọi đang xếp hàng bị từ chối và đánh thức bên gọi, các tác vụ khác bị loại bỏ.
     */
    void stopRuntime() {
        synchronized (runtimeLock) {
            runtimeState = RuntimeState.STOPPED;
            for (Runnable action : runtimeInputs) {
                if (action instanceof Call<?> call) {
                    call.cancel();
                }
            }
            runtimeInputs.clear();
            runtimeLock.notifyAll();
        }
    }

    private void startRuntimeWorkerIfNeededLocked() {
        if (runtimeWorker == null) {
            startRuntimeWorkerLocked();
        }
    }

    private void startRuntimeWorkerLocked() {
        Thread worker = Thread.ofVirtual().unstarted(this::runRuntime);
        runtimeWorker = worker;
        runtimeState = RuntimeState.ACTIVE;
        worker.start();
    }

    private void runRuntime() {
        CURRENT_RUNTIME.set(this);
        try {
            try {
                while (true) {
                    Runnable action;
                    synchronized (runtimeLock) {
                        if (runtimeState == RuntimeState.STOPPED) {
                            runtimeInputs.clear();
                            return;
                        }
                        action = runtimeInputs.poll();
                        if (action == null) {
                            return;
                        }
                        runtimeLock.notifyAll();
                    }
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        LOGGER.log(
                                Level.WARNING,
                                "Zone runtime action failed for map " + mapId + " zone " + zoneId,
                                exception);
                    }
                }
            } finally {
                synchronized (runtimeLock) {
                    if (runtimeWorker == Thread.currentThread()) {
                        runtimeWorker = null;
                        if (runtimeState == RuntimeState.STOPPED) {
                            runtimeInputs.clear();
                        } else if (runtimeInputs.isEmpty()) {
                            runtimeState = RuntimeState.FROZEN;
                        } else {
                            startRuntimeWorkerLocked();
                        }
                    }
                }
            }
        } finally {
            CURRENT_RUNTIME.remove();
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

    /** Gia nhập Zone, gắn Session và trao đổi hiện diện với các thành viên hiện có. */
    public boolean enter(Session session) {
        requireOutsideRuntimeWorker("enter");
        if (session == null || session.state() == SessionState.CLOSED || session.player() == null) {
            return false;
        }
        if (!matchesLocation(session.player())) {
            return false;
        }

        try {
            EnterDelivery result = tryCall(() -> {
                synchronized (this) {
                    if (session.state() == SessionState.CLOSED
                            || session.player() == null
                            || !matchesLocation(session.player())) {
                        return new EnterDelivery(false, List.of());
                    }
                    if (session.zone() != null) {
                        return new EnterDelivery(session.zone() == this && hasPlayer(session), List.of());
                    }

                    JoinResult admission = addPlayer(session);
                    if (admission.status() == JoinStatus.FULL
                            || admission.status() == JoinStatus.PLAYER_ID_CONFLICT) {
                        return new EnterDelivery(false, List.of());
                    }
                    session.bindZone(this);
                    if (admission.status() == JoinStatus.ALREADY_PRESENT) {
                        return new EnterDelivery(true, List.of());
                    }

                    return new EnterDelivery(
                            true, area.addPlayer(session, session.player(), admission.existing()));
                }
            });
            closeRejected(result.rejectedObservers());
            return result.accepted();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Di chuyển Player trong Zone owner rồi phát thông báo ra khu vực. */
    public boolean move(Session session, int x, int y) {
        requireOutsideRuntimeWorker("move");
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        try {
            MoveDelivery result = tryCall(() -> {
                synchronized (this) {
                    Player player = session.player();
                    if (player == null || player.isDead() || !hasPlayer(session)) {
                        return new MoveDelivery(false, List.of());
                    }
                    if (!player.move(x, y)) {
                        return new MoveDelivery(false, List.of());
                    }
                    return new MoveDelivery(true, area.move(session, player, members()));
                }
            });
            closeRejected(result.rejectedObservers());
            return result.moved();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Tách Session khỏi Zone và chụp trạng thái Player ổn định trong Zone owner. */
    public PlayerSaveData leave(Session session) {
        requireOutsideRuntimeWorker("leave");
        if (session == null || session.player() == null) {
            return null;
        }

        LeaveDelivery result = call(() -> {
            synchronized (this) {
                Player player = session.player();
                if (player == null) {
                    return new LeaveDelivery(null, List.of());
                }
                if (!hasPlayer(session)) {
                    session.clearZone(this);
                    return new LeaveDelivery(PlayerSaveData.capture(player), List.of());
                }
                if (!removePlayer(session)) {
                    return new LeaveDelivery(null, List.of());
                }
                List<Session> rejected = area.removePlayer(session, player.id(), members());
                session.clearZone(this);
                return new LeaveDelivery(PlayerSaveData.capture(player), rejected);
            }
        });
        closeRejected(result.rejectedObservers());
        return result.saveData();
    }

    /** Giữ một slot cho Session trước khi route owner tách Player khỏi Zone nguồn. */
    ReserveStatus reserve(Session session) {
        requireOutsideRuntimeWorker("reserve");
        return call(() -> reservePlayer(session));
    }

    /** Hủy slot đang giữ cho Session sau khi route owner không thể commit. */
    boolean cancel(Session session) {
        requireOutsideRuntimeWorker("cancel");
        return call(() -> cancelReservation(session));
    }

    /** Thử cho Session vào Zone và trả về các thành viên đã có trước khi gia nhập. */
    synchronized JoinResult addPlayer(Session session) {
        Objects.requireNonNull(session, "session");
        Player player = requirePlayer(session);
        Session existingSession = members.get(player.id());
        if (existingSession == session) {
            return new JoinResult(JoinStatus.ALREADY_PRESENT, List.of());
        }
        if (existingSession != null) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        Session reservedSession = reservedPlayers.get(player.id());
        if (reservedSession != null && reservedSession != session) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        if (reservedSession == session) {
            reservedPlayers.remove(player.id(), session);
        } else if (members.size() + reservedPlayers.size() >= maxPlayer) {
            return new JoinResult(JoinStatus.FULL, List.of());
        }
        List<Session> existing = List.copyOf(members.values());
        members.put(player.id(), session);
        return new JoinResult(JoinStatus.ADDED, existing);
    }

    /** Giữ một slot cho Session trước khi Player rời Zone nguồn. */
    synchronized ReserveStatus reservePlayer(Session session) {
        Objects.requireNonNull(session, "session");
        Player player = requirePlayer(session);
        Session existingMember = members.get(player.id());
        if (existingMember == session) {
            return ReserveStatus.ALREADY_PRESENT;
        }
        if (existingMember != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }

        Session existingReservation = reservedPlayers.get(player.id());
        if (existingReservation == session) {
            return ReserveStatus.ALREADY_RESERVED;
        }
        if (existingReservation != null) {
            return ReserveStatus.PLAYER_ID_CONFLICT;
        }
        if (members.size() + reservedPlayers.size() >= maxPlayer) {
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
            for (Monster monster : monsters.values()) {
                monster.removeEnemy(player.id());
            }
        }
        return removed;
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

    public synchronized List<MonsterSnapshot> monsterSnapshots() {
        return monsters.values().stream()
                .map(Monster::snapshot)
                .toList();
    }

    public synchronized boolean hasLiveMonster(int monsterId) {
        Monster monster = monsters.get(monsterId);
        return monster != null && monster.isAlive();
    }

    /** Damage bridge for Combat while the caller owns this Zone writer. */
    public synchronized Monster.Damage damageMonster(
            int monsterId,
            int attackerPlayerId,
            long damage,
            long nowMillis) {
        Monster monster = monsters.get(monsterId);
        return monster == null ? null : monster.injure(attackerPlayerId, damage, nowMillis, members.size());
    }

    /** Runs Monster lifecycle phases through this Zone writer in stable runtime order. */
    public void updateMonsters(long nowMillis, RandomGenerator random) {
        requireOutsideRuntimeWorker("updateMonsters");
        Objects.requireNonNull(random, "random");
        try {
            MonsterDelivery delivery = tryCall(() -> {
                synchronized (this) {
                    List<Session> rejected = new ArrayList<>();

                    for (Monster monster : monsters.values()) {
                        Monster.Move move = monster.updateMove(hostileLivingPlayers(monster));
                        if (move != null) {
                            rejected.addAll(area.monsterMove(move, members()));
                        }
                    }
                    for (Monster monster : monsters.values()) {
                        Monster.Respawn respawn = monster.updateRespawn(nowMillis);
                        if (respawn != null) {
                            rejected.addAll(area.monsterRespawn(respawn, members()));
                        }
                    }
                    for (Monster monster : monsters.values()) {
                        Monster.Attack attack = monster.updateAttack(
                                hostileLivingPlayers(monster), nowMillis, random);
                        if (attack == null) {
                            continue;
                        }
                        if (attack.killed()) {
                            for (Monster runtime : monsters.values()) {
                                runtime.removeEnemy(attack.playerId());
                            }
                        }
                        rejected.addAll(area.monsterAttack(attack, members()));
                    }
                    return new MonsterDelivery(rejected);
                }
            });
            closeRejected(delivery.rejectedObservers());
        } catch (RejectedExecutionException ignored) {
            // A stopped or saturated Zone cannot run another lifecycle tick.
        }
    }

    private List<Player> hostileLivingPlayers(Monster monster) {
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

    private static Player requirePlayer(Session session) {
        Player player = session.player();
        if (player == null) {
            throw new IllegalStateException("zone membership requires a bound player");
        }
        return player;
    }

    private boolean matchesLocation(Player player) {
        return player != null && player.mapId() == mapId && player.zoneId() == zoneId;
    }

    static void requireOutsideRuntimeWorker(String action) {
        if (CURRENT_RUNTIME.get() != null) {
            throw new IllegalStateException(
                    action + " must be called outside the Zone runtime writer");
        }
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    private record EnterDelivery(boolean accepted, List<Session> rejectedObservers) {
        private EnterDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record MoveDelivery(boolean moved, List<Session> rejectedObservers) {
        private MoveDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record LeaveDelivery(PlayerSaveData saveData, List<Session> rejectedObservers) {
        private LeaveDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record MonsterDelivery(List<Session> rejectedObservers) {
        private MonsterDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private static final class Call<T> implements Runnable {
        private final Supplier<T> action;
        private final String rejectionMessage;
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<T> result = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private Call(Supplier<T> action, int mapId, int zoneId) {
            this.action = Objects.requireNonNull(action, "action");
            this.rejectionMessage =
                    "Zone runtime stopped for map " + mapId + " zone " + zoneId;
        }

        @Override
        public void run() {
            try {
                result.set(action.get());
            } catch (RuntimeException | Error exception) {
                failure.set(exception);
                throw exception;
            } finally {
                completed.countDown();
            }
        }

        private void cancel() {
            failure.set(new RejectedExecutionException(rejectionMessage));
            completed.countDown();
        }

        private T await(boolean interrupted) {
            while (true) {
                try {
                    completed.await();
                    break;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            Throwable exception = failure.get();
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            return result.get();
        }
    }

    enum RuntimeState {
        ACTIVE,
        FROZEN,
        STOPPED
    }

    /** Kết quả của một lần thử gia nhập nguyên tử. */
    enum JoinStatus {
        ADDED,
        ALREADY_PRESENT,
        FULL,
        PLAYER_ID_CONFLICT
    }

    enum ReserveStatus {
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
