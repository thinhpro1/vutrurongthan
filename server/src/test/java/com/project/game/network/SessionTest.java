package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.SessionServices;
import com.project.game.account.AuthService;
import com.project.game.player.PlayerService;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.PlayerProfile;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.GameplayTestSupport;
import com.project.game.testsupport.MutableClock;
import com.project.game.monster.MonsterFactory;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.TestAccountRepository;
import com.project.game.testsupport.TestPlayerRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {
    @Test
    void startFailureClosesTransportAndRemovesRegisteredSession() {
        SessionManager manager = new SessionManager();
        AtomicBoolean transportClosed = new AtomicBoolean();
        ClientTransport transport = new ClientTransport() {
            @Override
            public InputStream input() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public OutputStream output() throws IOException {
                throw new IOException("output unavailable");
            }

            @Override
            public String remoteAddress() {
                return "127.0.0.1";
            }

            @Override
            public void close() {
                transportClosed.set(true);
            }
        };
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(), ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));

        assertThrows(IOException.class, session::start);

        assertTrue(transportClosed.get());
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, manager.onlineCount());
    }

    @Test
    void outboundQueueWritesBackToBackMessagesInFifoOrderWithoutInterleaving() throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"), manager,
                    new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 8,
                    TestServices.serverServices(), ClientConfig.defaults());
            List<Message> expected = List.of(
                    new Message(MessageName.DIALOG_OK, new byte[]{1}),
                    new Message(MessageName.START_CREATE_PLAYER_SCREEN, new byte[]{2, 3}),
                    new Message(MessageName.CREATE_PLAYER, new byte[]{4, 5, 6}));
            session.start();
            session.completeHandshake();
            output.reset();
            for (Message message : expected) {
                assertTrue(session.send(message));
            }
            waitForBytes(output, 15);

            ByteArrayInputStream wire = new ByteArrayInputStream(output.toByteArray());
            LegacyPacketCodec codec = new LegacyPacketCodec(1024);
            LegacyCipher cipher = new LegacyCipher("abc".getBytes(StandardCharsets.US_ASCII));
            assertEquals(expected.get(0), codec.readServerResponse(wire, cipher, true));
            assertEquals(expected.get(1), codec.readServerResponse(wire, cipher, true));
            assertEquals(expected.get(2), codec.readServerResponse(wire, cipher, true));
            assertEquals(0, wire.available());
            session.close();
        }
    }

    @Test
    void trySendRejectsFullQueueWithoutClosingSession() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerService players = new PlayerService(repository);
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session session = managedSession(
                services, players.create(101L, "alpha1", 0).player(), "user01");
        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(session, fullQueue);

        assertFalse(session.trySend(new Message(MessageName.PLAYER_MOVE)));
        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(0, repository.checkpointCalls.get());
        assertEquals(session, session.manager().findByAccount("user01"));

        repository.allowUpdate.countDown();
        session.close();
    }

    @Test
    void movementCleansFailedObserverOutsideZoneExecution() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerService players = new PlayerService(repository);
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session mover = managedSession(
                services, players.create(101L, "alpha1", 0).player(), "user01");
        Session observer = managedSession(
                services, players.create(202L, "beta22", 0).player(), "user02");
        gameplay.mapService().finishLoad(mover);
        gameplay.mapService().finishLoad(observer);
        GameplayTestSupport.drain(mover);
        GameplayTestSupport.drain(observer);

        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(observer, fullQueue);

        AtomicBoolean moved = new AtomicBoolean();
        Thread movement = Thread.ofVirtual().start(() ->
                moved.set(gameplay.mapService().movePlayer(mover, 1260, 640)));

        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertEquals(1260, mover.player().x());
        assertEquals(640, mover.player().y());
        assertTrue(movement.isAlive());

        CountDownLatch nextZoneAction = new CountDownLatch(1);
        assertTrue(gameplay.findZone(0, 0).submit(nextZoneAction::countDown));
        assertTrue(nextZoneAction.await(5, TimeUnit.SECONDS));
        assertFalse(moved.get());

        repository.allowUpdate.countDown();
        movement.join(1_000);
        assertFalse(movement.isAlive());
        assertTrue(moved.get());
        assertEquals(SessionState.CLOSED, observer.state());

        mover.close();
    }

    @Test
    void disconnectWaitsForSaturatedZoneQueueBeforeCheckpoint() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerService players = new PlayerService(repository);
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session session = managedSession(
                services, players.create(101L, "alpha1", 0).player(), "user01");
        assertTrue(gameplay.mapService().finishLoad(session));
        GameplayTestSupport.drain(session);
        var zone = gameplay.findZone(0, 0);

        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            session.bindPlayer(session.player().withPosition(1260, 640));
            priorStarted.countDown();
            try {
                if (!releasePrior.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("prior Zone action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));
        for (int index = 0; index < 1024; index++) {
            assertTrue(zone.submit(() -> {
            }));
        }

        Thread close = Thread.ofVirtual().start(session::close);
        waitForCloseStarted(session);
        assertEquals(1, gameplay.mapService().memberCount(0, 0));
        assertFalse(repository.updateEntered.await(100, TimeUnit.MILLISECONDS));

        releasePrior.countDown();
        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertEquals(0, gameplay.mapService().memberCount(0, 0));
        repository.allowUpdate.countDown();
        close.join(2_000);

        assertFalse(close.isAlive());
        assertEquals(1260, delegate.requireByAccountId(101L).x());
        assertEquals(640, delegate.requireByAccountId(101L).y());
        assertEquals(1, repository.checkpointCalls.get());
    }

    @Test
    void finishLoadCleansFailedObserverOutsideZoneExecution() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerService players = new PlayerService(repository);
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session observer = managedSession(
                services, players.create(101L, "alpha1", 0).player(), "user01");
        Session joining = managedSession(
                services, players.create(202L, "beta22", 0).player(), "user02");
        assertTrue(gameplay.mapService().finishLoad(observer));
        GameplayTestSupport.drain(observer);

        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(observer, fullQueue);

        AtomicBoolean joined = new AtomicBoolean();
        Thread finishLoad = Thread.ofVirtual().start(() ->
                joined.set(gameplay.mapService().finishLoad(joining)));

        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertTrue(finishLoad.isAlive());
        CountDownLatch nextZoneAction = new CountDownLatch(1);
        assertTrue(gameplay.findZone(0, 0).submit(nextZoneAction::countDown));
        assertTrue(nextZoneAction.await(5, TimeUnit.SECONDS));
        assertEquals(1, gameplay.mapService().memberCount(0, 0));

        repository.allowUpdate.countDown();
        finishLoad.join(1_000);
        assertFalse(finishLoad.isAlive());
        assertTrue(joined.get());
        assertEquals(SessionState.CLOSED, observer.state());
        assertEquals(1, gameplay.mapService().memberCount(0, 0));

        joining.close();
    }

    @Test
    void closeKeepsAccountReservedUntilFinalPlayerCheckpointCompletes() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices maps = new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterFactory(resources));
        PlayerService players = new PlayerService(repository);
        PlayerProfile player = players.create(101L, "alpha1", 0).player().withHp(77);
        SessionServices services = TestServices.serverServices(auth, resources, maps, players);
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, ClientConfig.defaults());
        assertTrue(manager.beginAccountAdmission(session, 101L, "user01"));
        manager.finishAccountAdmission(session, true);
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);

        Thread close = Thread.ofVirtual().start(session::close);
        assertTrue(repository.updateEntered.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(manager.findByAccount("user01") == session);

        repository.allowUpdate.countDown();
        close.join(1_000);
        assertTrue(!close.isAlive());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(77L, delegate.requireByAccountId(101L).hp());
    }

    @Test
    void disconnectCheckpointsLatestPositionAfterInFlightZoneMutation() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        PlayerService players = new PlayerService(repository);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameplayServices gameplay = new GameplayServices(GameResources.unavailable());
        SessionServices services = TestServices.serverServices(
                auth, GameResources.unavailable(), gameplay, players);
        PlayerProfile moverProfile = players.create(101L, "alpha1", 0).player();
        PlayerProfile observerProfile = players.create(202L, "beta22", 0).player();
        Session mover = managedSession(services, moverProfile, "user01");
        Session observer = managedSession(services, observerProfile, "user02");
        gameplay.mapService().finishLoad(mover);
        gameplay.mapService().finishLoad(observer);
        GameplayTestSupport.drain(mover);
        GameplayTestSupport.drain(observer);

        GameplayTestSupport.BlockingOfferQueue observerQueue =
                new GameplayTestSupport.BlockingOfferQueue();
        GameplayTestSupport.replaceSendQueue(observer, observerQueue);
        AtomicBoolean moved = new AtomicBoolean();
        Thread movement = Thread.ofVirtual().start(() ->
                moved.set(gameplay.mapService().movePlayer(mover, 1260, 640)));
        assertTrue(observerQueue.offerEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));

        Thread close = Thread.ofVirtual().start(mover::close);
        waitForCloseStarted(mover);
        assertFalse(repository.updateEntered.await(100, java.util.concurrent.TimeUnit.MILLISECONDS));

        observerQueue.releaseOffer.countDown();
        movement.join(1_000);
        assertFalse(movement.isAlive());
        assertTrue(moved.get());
        assertTrue(repository.updateEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        repository.allowUpdate.countDown();
        close.join(1_000);

        assertFalse(close.isAlive());
        assertEquals(1260, delegate.requireByAccountId(101L).x());
        assertEquals(640, delegate.requireByAccountId(101L).y());
        assertEquals(1, repository.checkpointCalls.get());
        assertEquals(null, mover.manager().findByAccount("user01"));
        assertEquals(SessionState.CLOSED, mover.state());
    }

    @Test
    void disconnectCheckpointsLatestHpAfterInFlightMonsterAttack() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices gameplay = new GameplayServices(
                GameResources.fromFrameRoot(
                        java.nio.file.Path.of("resources", "json"),
                        com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                        2,
                        com.project.game.testsupport.MonsterTestSupport.canonicalRepository()),
                clock,
                new java.util.Random(0L));
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        PlayerService players = new PlayerService(repository);
        AuthService auth = new AuthService(new TestAccountRepository());
        SessionServices services = TestServices.serverServices(
                auth, GameResources.unavailable(), gameplay, players);
        PlayerProfile targetProfile = players.create(101L, "alpha1", 0).player()
                .withLocation(1, 0, 1250, 648)
                .withHp(100);
        PlayerProfile observerProfile = players.create(202L, "beta22", 0).player()
                .withLocation(1, 0, 1250, 648);
        Session target = managedSession(services, targetProfile, "user01");
        Session observer = managedSession(services, observerProfile, "user02");
        gameplay.mapService().finishLoad(target);
        gameplay.mapService().finishLoad(observer);
        GameplayTestSupport.drain(target);
        GameplayTestSupport.drain(observer);
        assertTrue(gameplay.combatService().attackMonster(target, 101, 10L));
        GameplayTestSupport.drain(target);
        GameplayTestSupport.drain(observer);
        clock.advanceMillis(1L);

        BlockingAttackQueue observerQueue = new BlockingAttackQueue();
        GameplayTestSupport.replaceSendQueue(observer, observerQueue);
        Thread lifecycle = Thread.ofVirtual().start(gameplay.monsterManager()::update);
        assertTrue(observerQueue.attackEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(90, target.player().hp());

        Thread close = Thread.ofVirtual().start(target::close);
        waitForCloseStarted(target);
        assertFalse(repository.updateEntered.await(100, java.util.concurrent.TimeUnit.MILLISECONDS));

        observerQueue.releaseAttack.countDown();
        lifecycle.join(1_000);
        assertFalse(lifecycle.isAlive());
        assertTrue(repository.updateEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        repository.allowUpdate.countDown();
        close.join(1_000);

        assertFalse(close.isAlive());
        assertEquals(90, delegate.requireByAccountId(101L).hp());
        assertEquals(1, repository.checkpointCalls.get());
        assertEquals(null, target.manager().findByAccount("user01"));
        assertEquals(SessionState.CLOSED, target.state());
    }

    @Test
    void readerThreadCloseDoesNotSelfInterruptBeforeCheckpoint() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        PlayerService players = new PlayerService(repository);
        PlayerProfile player = players.create(101L, "alpha1", 0).player().withHp(77);
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AuthService(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterFactory(GameResources.unavailable())),
                        players),
                ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));
        assertTrue(manager.beginAccountAdmission(session, 101L, "user01"));
        manager.finishAccountAdmission(session, true);
        session.bindPlayer(player);

        session.start();
        assertTrue(repository.updateEntered.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(repository.interruptedAtCheckpoint.get());
        assertTrue(manager.findByAccount("user01") == session);
        assertEquals(1, repository.checkpointCalls.get());

        repository.allowUpdate.countDown();
        waitForAccountRelease(manager);
        waitForClosed(session);
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(77, delegate.requireByAccountId(101L).hp());
    }

    @Test
    void preInterruptedCloseStillCheckpointsAndRestoresInterruptStatus() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        PlayerService players = new PlayerService(repository);
        PlayerProfile player = players.create(101L, "alpha1", 0).player().withHp(66);
        SessionManager manager = new SessionManager();
        TestTransport transport = new TestTransport();
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AuthService(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterFactory(GameResources.unavailable())),
                        players),
                ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));
        assertTrue(manager.beginAccountAdmission(session, 101L, "user01"));
        manager.finishAccountAdmission(session, true);
        session.bindPlayer(player);
        AtomicBoolean interruptedAfterClose = new AtomicBoolean();

        Thread close = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
            session.close();
            interruptedAfterClose.set(Thread.currentThread().isInterrupted());
        });

        assertTrue(repository.updateEntered.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(repository.interruptedAtCheckpoint.get());
        assertTrue(manager.findByAccount("user01") == session);
        assertEquals(1, repository.checkpointCalls.get());
        repository.allowUpdate.countDown();
        close.join(1_000);

        assertFalse(close.isAlive());
        assertTrue(interruptedAfterClose.get());
        assertTrue(transport.isClosed());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(66, delegate.requireByAccountId(101L).hp());
    }

    @Test
    void checkpointFailureStillReleasesAccountAndSession() {
        TestPlayerRepository repository = new TestPlayerRepository();
        repository.failUpdate(true);
        PlayerService players = new PlayerService(repository);
        PlayerProfile player = players.create(101L, "alpha1", 0).player();
        SessionManager manager = new SessionManager();
        TestTransport transport = new TestTransport();
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AuthService(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterFactory(GameResources.unavailable())),
                        players),
                ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));
        assertTrue(manager.beginAccountAdmission(session, 101L, "user01"));
        manager.finishAccountAdmission(session, true);
        session.bindPlayer(player);

        session.close();

        assertEquals(SessionState.CLOSED, session.state());
        assertTrue(transport.isClosed());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(0, manager.onlineCount());
    }

    private static final class BlockingPlayerRepository implements PlayerRepository {
        private final TestPlayerRepository delegate;
        private final CountDownLatch updateEntered = new CountDownLatch(1);
        private final CountDownLatch allowUpdate = new CountDownLatch(1);
        private final AtomicBoolean interruptedAtCheckpoint = new AtomicBoolean();
        private final AtomicInteger checkpointCalls = new AtomicInteger();

        private BlockingPlayerRepository(TestPlayerRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PlayerRecord> findByAccountId(long accountId) {
            return delegate.findByAccountId(accountId);
        }

        @Override
        public PlayerRecord create(PlayerRecord initialWithoutId) {
            return delegate.create(initialWithoutId);
        }

        @Override
        public void updateCheckpoint(PlayerProfile player, Instant playedAt) {
            checkpointCalls.incrementAndGet();
            interruptedAtCheckpoint.set(Thread.currentThread().isInterrupted());
            updateEntered.countDown();
            try {
                if (!allowUpdate.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new PlayerRepositoryException("timed out in test");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PlayerRepositoryException("interrupted in test", exception);
            }
            delegate.updateCheckpoint(player, playedAt);
        }
    }

    private static Session managedSession(
            SessionServices services,
            PlayerProfile player,
            String accountName) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 16,
                services, ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));
        assertTrue(manager.beginAccountAdmission(session, player.accountId(), accountName));
        manager.finishAccountAdmission(session, true);
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    private static final class BlockingAttackQueue extends LinkedBlockingQueue<Message> {
        private final CountDownLatch attackEntered = new CountDownLatch(1);
        private final CountDownLatch releaseAttack = new CountDownLatch(1);
        private final AtomicBoolean blockAttack = new AtomicBoolean(true);

        @Override
        public boolean offer(Message message) {
            if (message.command() == MessageName.MONSTER_ATTACK
                    && blockAttack.compareAndSet(true, false)) {
                attackEntered.countDown();
                try {
                    releaseAttack.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("attack gate interrupted", exception);
                }
            }
            return super.offer(message);
        }
    }

    private static void waitForClosed(Session session) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (session.state() == SessionState.CLOSED) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(SessionState.CLOSED, session.state(), "timed out waiting for session close");
    }

    private static void waitForCloseStarted(Session session) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (session.state() != SessionState.CLOSED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(SessionState.CLOSED, session.state(), "close flow did not start");
    }

    private static void waitForAccountRelease(SessionManager manager) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (manager.findByAccount("user01") == null) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(null, manager.findByAccount("user01"), "timed out waiting for account release");
    }

    private static void waitForBytes(ByteArrayOutputStream output, int expectedBytes) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (output.size() >= expectedBytes) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expectedBytes, output.size(), "timed out waiting for outbound frames");
    }
}
