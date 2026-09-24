package com.project.game.map;

import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.MonsterAttack;
import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.PlayerProfile;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Concurrent runtime membership for one map zone. */
public final class Zone {
    private static final int MONSTER_CHASE_LEASH = 1200;
    private static final int DEFAULT_RUNTIME_INPUT_CAPACITY = 1024;
    private static final Logger LOGGER = Logger.getLogger(Zone.class.getName());
    private final int mapId;
    private final int zoneId;
    private final int maxPlayer;
    private final ConcurrentHashMap<Integer, Session> members = new ConcurrentHashMap<>();
    private final LinkedHashMap<Integer, Monster> monsters = new LinkedHashMap<>();
    private final ArrayBlockingQueue<Runnable> runtimeInputs;
    private final Object runtimeLock = new Object();
    private RuntimeState runtimeState = RuntimeState.FROZEN;
    private Thread runtimeWorker;

    public Zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters) {
        this(mapId, zoneId, maxPlayer, monsters, DEFAULT_RUNTIME_INPUT_CAPACITY);
    }

    Zone(
            int mapId,
            int zoneId,
            int maxPlayer,
            List<Monster> monsters,
            int inputCapacity) {
        if (maxPlayer <= 0) {
            throw new IllegalArgumentException("maxPlayer must be positive");
        }
        if (inputCapacity <= 0) {
            throw new IllegalArgumentException("inputCapacity must be positive");
        }
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.maxPlayer = maxPlayer;
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
     * Enqueues one action for this Zone's serialized runtime writer. The offer is deliberately
     * non-blocking: callers must handle a full input queue explicitly.
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

    /** Runs one action on this Zone writer and rejects immediately when the bounded queue is full. */
    <T> T tryCall(Supplier<T> action) {
        return executeCall(action, false);
    }

    /** Runs one required action on this Zone writer, waiting for bounded queue capacity if needed. */
    <T> T call(Supplier<T> action) {
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
     * Stops this Zone runtime permanently. An action already running is allowed to finish;
     * queued calls are rejected and wake their callers; other queued actions are discarded.
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

    public synchronized boolean add(Session session) {
        return addAndSnapshot(session).status() == JoinStatus.ADDED;
    }

    public synchronized boolean remove(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        return members.remove(player.id(), session);
    }

    /**
     * Returns the members present before an added session joined. Admission, capacity, and the
     * snapshot share this Zone monitor so a join cannot miss another concurrent join.
     */
    synchronized JoinResult addAndSnapshot(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        Session existingSession = members.get(player.id());
        if (existingSession == session) {
            return new JoinResult(JoinStatus.ALREADY_PRESENT, List.of());
        }
        if (existingSession != null) {
            return new JoinResult(JoinStatus.PLAYER_ID_CONFLICT, List.of());
        }
        if (members.size() >= maxPlayer) {
            return new JoinResult(JoinStatus.FULL, List.of());
        }
        List<Session> existing = List.copyOf(members.values());
        members.put(player.id(), session);
        return new JoinResult(JoinStatus.ADDED, existing);
    }

    synchronized boolean canAccept(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        Session existing = members.get(player.id());
        if (existing == session) {
            return true;
        }
        if (existing != null) {
            return false;
        }
        return members.size() < maxPlayer;
    }

    public synchronized boolean contains(Session session) {
        if (session == null || session.player() == null) {
            return false;
        }
        return members.get(session.player().id()) == session;
    }

    public synchronized boolean containsPlayer(int playerId) {
        return members.containsKey(playerId);
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized List<Session> snapshot() {
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

    public synchronized Optional<Monster.Damage> damageMonster(
            int monsterId,
            int attackerPlayerId,
            long damage,
            long nowMillis) {
        Monster monster = monsters.get(monsterId);
        if (monster == null) {
            return Optional.empty();
        }

        long delay = respawnDelayMillis(members.size());
        return monster.injure(attackerPlayerId, damage, nowMillis, delay);
    }

    public synchronized List<MonsterAttack> attackDueMonsters(
            long nowMillis,
            RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        List<MonsterAttack> attacks = new java.util.ArrayList<>();
        for (Monster monster : monsters.values()) {
            if (!monster.beginAttack(nowMillis)) {
                continue;
            }
            List<Session> eligible = monster.enemyPlayerIds().stream()
                    .map(members::get)
                    .filter(Objects::nonNull)
                    .filter(member -> member.state() != SessionState.CLOSED)
                    .filter(member -> member.player() != null)
                    .filter(member -> {
                        PlayerProfile player = member.player();
                        return player.mapId() == mapId
                                && player.zoneId() == zoneId
                                && player.hp() > 0L
                                && isWithinMonsterAttackRange(monster.snapshot(), player);
                    })
                    .toList();
            if (eligible.isEmpty()) {
                continue;
            }
            Session target = eligible.get(random.nextInt(eligible.size()));
            PlayerProfile player = target.player();
            int hpAfter = Math.toIntExact(
                    Math.max(0L, (long) player.hp() - monster.damage()));
            boolean killed = hpAfter == 0L;
            target.bindPlayer(player.withHp(hpAfter));
            if (killed) {
                for (Monster runtime : monsters.values()) {
                    runtime.removeEnemy(player.id());
                }
            }
            attacks.add(new MonsterAttack(
                    monster.id(), player.id(), monster.damage(), hpAfter, killed));
        }
        return List.copyOf(attacks);
    }

    public synchronized List<Monster.Move> moveMonsters() {
        List<Monster.Move> moves = new ArrayList<>();
        for (Monster monster : monsters.values()) {
            if (!monster.isAlive()) {
                continue;
            }

            List<Session> chaseEligible = chaseEligibleMembers(monster);
            if (chaseEligible.stream().anyMatch(member ->
                    isWithinMonsterAttackRange(monster.snapshot(), member.player()))) {
                continue;
            }

            Session target = nearestChaseTarget(monster, chaseEligible);
            Optional<Monster.Move> moved = target == null
                    ? monster.patrol()
                    : monster.moveTo(target.player().x());
            moved.ifPresent(moves::add);
        }
        return List.copyOf(moves);
    }

    public synchronized List<Monster.Respawn> respawnDueMonsters(long nowMillis) {
        return monsters.values().stream()
                .map(monster -> monster.updateRespawn(nowMillis))
                .flatMap(Optional::stream)
                .toList();
    }

    static long respawnDelayMillis(int playerCount) {
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must be non-negative");
        }
        return Math.max(10_000L - 1_000L * playerCount, 5_000L);
    }

    private static boolean isWithinMonsterAttackRange(
            MonsterSnapshot monster,
            PlayerProfile player) {
        return squaredDistance(monster, player) < 900L * 900L;
    }

    private Session nearestChaseTarget(
            Monster monster,
            List<Session> chaseEligible) {
        MonsterSnapshot snapshot = monster.snapshot();
        return chaseEligible.stream()
                .min(Comparator
                        .comparingLong((Session member) ->
                                squaredDistance(snapshot, member.player()))
                        .thenComparingInt(member -> member.player().id()))
                .orElse(null);
    }

    private List<Session> chaseEligibleMembers(Monster monster) {
        return hostileLivingMembers(monster).stream()
                .filter(member -> Math.abs(
                        (long) member.player().x() - monster.xFirst())
                        <= MONSTER_CHASE_LEASH)
                .toList();
    }

    private List<Session> hostileLivingMembers(Monster monster) {
        return monster.enemyPlayerIds().stream()
                .map(members::get)
                .filter(Objects::nonNull)
                .filter(member -> member.state() != SessionState.CLOSED)
                .filter(member -> member.player() != null)
                .filter(member -> {
                    PlayerProfile player = member.player();
                    return player.mapId() == mapId
                            && player.zoneId() == zoneId
                            && player.hp() > 0L;
                })
                .toList();
    }

    private static long squaredDistance(
            MonsterSnapshot monster,
            PlayerProfile player) {
        long dx = (long) monster.x() - player.x();
        long dy = (long) monster.y() - player.y();
        return dx * dx + dy * dy;
    }

    private static PlayerProfile requirePlayer(Session session) {
        PlayerProfile player = session.player();
        if (player == null) {
            throw new IllegalStateException("zone membership requires a bound player");
        }
        return player;
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

    /** The outcome of one atomic admission attempt. */
    enum JoinStatus {
        ADDED,
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
