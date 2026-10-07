package com.project.game.map;

import com.project.game.monster.Monster.Snapshot;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Runtime của một khu vực bản đồ: writer, membership và entity state. */
public final class Zone {
    private final int mapId;
    private final int zoneId;
    private final int maxPlayer;
    private final AreaService area;
    private final LinkedHashMap<Integer, Session> members = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Session> reservedPlayers = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    private final ZoneWriter writer;

    public Zone(
            int mapId,
            int zoneId,
            int maxPlayer,
            List<Monster> monsters,
            AreaService area) {
        this(mapId, zoneId, maxPlayer, monsters, ZoneWriter.DEFAULT_INPUT_CAPACITY, area);
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
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.maxPlayer = maxPlayer;
        this.writer = new ZoneWriter(mapId, zoneId, inputCapacity);
        this.area = Objects.requireNonNull(area, "area");
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
        return enter(session, null);
    }

    /** Notify the route owner of new admission on this writer, before delivery. */
    boolean enter(Session session, Runnable admitted) {
        requireOutsideRuntimeWorker("enter");
        if (session == null || session.state() == SessionState.CLOSED || session.player() == null) {
            return false;
        }
        if (!matchesLocation(session.player())) {
            return false;
        }

        List<Session> rejected = new ArrayList<>();
        try {
            boolean accepted = tryCall(() -> enterPlayer(session, admitted, rejected));
            closeRejected(rejected);
            return accepted;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private synchronized boolean enterPlayer(
            Session session, Runnable admitted, List<Session> rejected) {
        if (session.state() == SessionState.CLOSED
                || session.player() == null
                || !matchesLocation(session.player())) {
            return false;
        }
        if (session.zone() != null) {
            return session.zone() == this && hasPlayer(session);
        }

        JoinResult admission = addPlayer(session);
        if (admission.status() == JoinStatus.FULL
                || admission.status() == JoinStatus.PLAYER_ID_CONFLICT) {
            return false;
        }
        session.bindZone(this);
        if (admission.status() == JoinStatus.ALREADY_PRESENT) {
            return true;
        }
        if (admitted != null) {
            admitted.run();
        }

        rejected.addAll(area.addPlayer(session, session.player(), admission.existing()));
        return true;
    }

    /** Di chuyển Player trong Zone owner rồi phát thông báo ra khu vực. */
    public boolean move(Session session, int x, int y) {
        requireOutsideRuntimeWorker("move");
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        List<Session> rejected = new ArrayList<>();
        try {
            boolean moved = tryCall(() -> movePlayer(session, x, y, rejected));
            closeRejected(rejected);
            return moved;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private synchronized boolean movePlayer(
            Session session, int x, int y, List<Session> rejected) {
        Player player = session.player();
        if (player == null || player.isDead() || !hasPlayer(session)) {
            return false;
        }
        if (!player.move(x, y)) {
            return false;
        }

        rejected.addAll(area.move(session, player, members()));
        return true;
    }

    /** Tách Session khỏi Zone và chụp trạng thái Player ổn định trong Zone owner. */
    public PlayerSaveData leave(Session session) {
        requireOutsideRuntimeWorker("leave");
        if (session == null || session.player() == null) {
            return null;
        }

        List<Session> rejected = new ArrayList<>();
        PlayerSaveData saved = call(() -> {
            leavePlayer(session, rejected);
            if (session.zone() != null) {
                return null;
            }
            return PlayerSaveData.capture(session.player());
        });
        closeRejected(rejected);
        return saved;
    }

    /** Route owner drains this Zone without capturing a possibly foreign-owned Player. */
    void leave(Session session, List<Session> rejected) {
        requireOutsideRuntimeWorker("leave");
        call(() -> {
            leavePlayer(session, rejected);
            return null;
        });
    }

    private synchronized void leavePlayer(Session session, List<Session> rejected) {
        Player player = session.player();
        if (player == null) {
            return;
        }
        cancelReservation(session);
        boolean removed = removePlayer(session);
        session.clearZone(this);
        if (removed) {
            rejected.addAll(area.removePlayer(session, player.id(), members()));
        }
    }

    /** Checks combat target state in this Zone's writer. */
    public boolean canTargetMonster(Session session, int monsterId) {
        requireOutsideRuntimeWorker("canTargetMonster");
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        try {
            return tryCall(() -> canTarget(session, monsterId));
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private synchronized boolean canTarget(Session session, int monsterId) {
        Player player = session.player();
        if (session.state() == SessionState.CLOSED || player == null) {
            return false;
        }
        if (session.zone() != this || members.get(player.id()) != session) {
            return false;
        }

        Monster monster = monsters.get(monsterId);
        return player.canTarget(monster);
    }

    /** Applies one Player attack and delivers its same-Zone effects in writer order. */
    public boolean attackMonster(Session session, int monsterId, long nowMillis) {
        requireOutsideRuntimeWorker("attackMonster");
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        List<Session> rejected = new ArrayList<>();
        try {
            boolean attacked = tryCall(
                    () -> attack(session, monsterId, nowMillis, rejected));
            closeRejected(rejected);
            return attacked;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private synchronized boolean attack(
            Session session, int monsterId, long nowMillis, List<Session> rejected) {
        Player player = session.player();
        if (session.state() == SessionState.CLOSED || player == null) {
            return false;
        }
        if (session.zone() != this || members.get(player.id()) != session) {
            return false;
        }

        Monster monster = monsters.get(monsterId);
        if (monster == null) {
            return false;
        }
        Monster.Damage result = player.attackMonster(monster, nowMillis, members.size());
        if (result == null) {
            return false;
        }

        rejected.addAll(area.monsterDamage(result, members()));
        if (result.killed() && result.potentialReward() > 0L) {
            if (session.state() != SessionState.CLOSED && !area.potential(session, player.potential())) {
                rejected.add(session);
            }
        }
        return true;
    }

    /** Returns an immutable view of the current Monster state in writer order. */
    public synchronized List<Snapshot> monsterSnapshots() {
        List<Snapshot> snapshots = new ArrayList<>(monsters.size());
        for (Monster monster : monsters.values()) {
            snapshots.add(monster.snapshot());
        }
        return List.copyOf(snapshots);
    }

    /** Runs the current Monster lifecycle through this Zone writer. */
    public void updateMonsters(long nowMillis, RandomGenerator random) {
        requireOutsideRuntimeWorker("updateMonsters");
        Objects.requireNonNull(random, "random");
        List<Session> rejected = new ArrayList<>();
        call(() -> {
            updateMonstersOnWriter(nowMillis, random, rejected);
            return null;
        });
        closeRejected(rejected);
    }

    private synchronized void updateMonstersOnWriter(
            long nowMillis, RandomGenerator random, List<Session> rejected) {
        for (Monster monster : monsters.values()) {
            boolean wasAlive = monster.isAlive();
            int oldX = monster.x();
            int oldY = monster.y();
            Monster.Attack attack = monster.update(
                    hostileLivingPlayers(monster), nowMillis, random);

            if (!wasAlive && monster.isAlive()) {
                Monster.Respawn respawn = new Monster.Respawn(
                        monster.id(), monster.levelStatus(), monster.hp());
                rejected.addAll(area.monsterRespawn(respawn, members()));
            } else if (oldX != monster.x() || oldY != monster.y()) {
                Monster.Move move = new Monster.Move(
                        monster.id(), monster.x(), monster.y(), monster.moveDir());
                rejected.addAll(area.monsterMove(move, members()));
            }

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
        if (session.state() == SessionState.CLOSED) {
            return ReserveStatus.CLOSED;
        }
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

    /** Hoàn tất detach sau removePlayer và cập nhật state bởi route owner trên writer này. */
    synchronized void detach(Session session, List<Session> rejected) {
        if (!writer.isCurrent()) {
            throw new IllegalStateException("detach requires this Zone writer");
        }
        session.clearZone(this);
        rejected.addAll(area.removePlayer(session, session.player().id(), members()));
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

    /** Infrastructure entry points must not block a gameplay writer. */
    public static void requireOutsideRuntimeWorker(String action) {
        ZoneWriter.requireOutsideWriter(action);
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    // Các bridge nội bộ cho route owner và kiểm tra lifecycle.
    boolean submit(Runnable action) {
        return writer.submit(action);
    }

    <T> T tryCall(Supplier<T> action) {
        return writer.tryCall(action);
    }

    <T> T call(Supplier<T> action) {
        return writer.call(action);
    }

    ZoneWriter.State runtimeState() {
        return writer.state();
    }

    void stopRuntime() {
        writer.stop();
    }

    /** Kết quả của một lần thử gia nhập nguyên tử. */
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
