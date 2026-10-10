package com.project.game.network;

import com.project.game.testsupport.TestPlayers;
import com.project.game.testsupport.TestServices;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.SessionServices;
import com.project.game.account.AccountAuth;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.Player;
import com.project.game.player.PlayerManager;
import com.project.game.player.PlayerSaveData;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.map.MapManager;
import com.project.game.map.Zone;
import com.project.game.map.ZoneTestHooks;
import com.project.game.service.AreaService;
import com.project.game.testsupport.GameplayTestSupport;
import com.project.game.testsupport.MutableClock;
import com.project.game.monster.MonsterManager;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {
    @Test
    void zoneIsReadThroughThePlayerAndLeaveNeedsTheExpectedZone() {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(), ClientConfig.defaults());
        Zone first = new Zone(0, 0, 10, List.of(),
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
        Zone second = new Zone(0, 1, 10, List.of(),
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        session.bindPlayer(player);
        assertSame(session, player.session());

        player.enterZone(first);
        assertSame(first, session.zone());

        player.leaveZone(second);
        assertSame(first, session.zone());

        player.leaveZone(first);
        assertNull(session.zone());
    }

    private static Player createPlayer(PlayerRepository repository,
                                       long accountId, String name, int gender) {
        Player initial = Player.create(accountId, name, gender);
        return repository.create(PlayerRecord.withoutId(PlayerSaveData.capture(initial)))
                .toPlayer(0);
    }

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
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerRepository players = repository;
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session session = managedSession(
                services, createPlayer(players, 101L, "alpha1", 0), "user01");
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
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerRepository players = repository;
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session mover = managedSession(
                services, createPlayer(players, 101L, "alpha1", 0), "user01");
        Session observer = managedSession(
                services, createPlayer(players, 202L, "beta22", 0), "user02");
        gameplay.mapManager().finishLoad(mover);
        gameplay.mapManager().finishLoad(observer);
        GameplayTestSupport.drain(mover);
        GameplayTestSupport.drain(observer);

        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(observer, fullQueue);

        AtomicBoolean moved = new AtomicBoolean();
        Thread movement = Thread.ofVirtual().start(() ->
                moved.set(gameplay.movePlayer(mover, 1260, 640)));

        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertEquals(1260, mover.player().x());
        assertEquals(640, mover.player().y());
        assertTrue(movement.isAlive());

        CountDownLatch nextZoneAction = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(gameplay.findZone(0, 0), nextZoneAction::countDown));
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
    void combatRejectionCheckpointsRewardOutsideWriter() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        MutableClock clock = new MutableClock(1_000_000L);
        GameResources resources = GameResources.fromFrameRoot(
                java.nio.file.Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices gameplay = new GameplayServices(resources, clock);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Player player = createPlayer(repository, 101L, "alpha1", 0);
        player.changeMap(1, 0, 1250, 648);
        Session attacker = managedSession(services, player, "user01");
        assertTrue(gameplay.mapManager().finishLoad(attacker));
        GameplayTestSupport.drain(attacker);
        for (int hit = 0; hit < 29; hit++) {
            assertTrue(gameplay.attackMonster(attacker, 101));
            GameplayTestSupport.drain(attacker);
        }
        Zone zone = gameplay.findZone(1, 0);
        RejectDeathQueue rejectedOutput = new RejectDeathQueue();
        GameplayTestSupport.replaceSendQueue(attacker, rejectedOutput);
        AtomicReference<Thread> attackCaller = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean attacked = new AtomicBoolean();
        Thread attack = Thread.ofVirtual().start(() -> {
            attackCaller.set(Thread.currentThread());
            try {
                attacked.set(gameplay.attackMonster(attacker, 101));
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        try {
            assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
            assertSame(attackCaller.get(), repository.checkpointThread.get());
            assertNotNull(rejectedOutput.writerThread.get());
            assertNotSame(rejectedOutput.writerThread.get(), repository.checkpointThread.get());
            assertEquals(11L, repository.savedPlayer.get().potential());
            assertEquals(11L, attacker.player().potential());
            assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
            assertEquals(SessionState.CLOSED, attacker.state());
            assertFalse(zone.hasPlayer(attacker.player()));
            assertNull(attacker.zone());
            assertSame(attacker, attacker.manager().findByAccount("user01"));
            assertTrue(attack.isAlive());

            CountDownLatch nextZoneAction = new CountDownLatch(1);
            assertTrue(ZoneTestHooks.submit(zone, nextZoneAction::countDown));
            assertTrue(nextZoneAction.await(5, TimeUnit.SECONDS));

            repository.allowUpdate.countDown();
            attack.join(5_000);
            assertFalse(attack.isAlive());
            assertNull(failure.get());
            assertTrue(attacked.get());
            assertEquals(1, repository.checkpointCalls.get());
            assertEquals(11L, delegate.requireByAccountId(101L).potential());
            assertNull(attacker.manager().findByAccount("user01"));
        } finally {
            repository.allowUpdate.countDown();
            attack.join(5_000);
        }
    }

    @Test
    void disconnectWaitsForSaturatedZoneQueueBeforeCheckpoint() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerRepository players = repository;
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session session = managedSession(
                services, createPlayer(players, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        var zone = gameplay.findZone(0, 0);

        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(zone, () -> {
            session.player().move(1260, 640);
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
            assertTrue(ZoneTestHooks.submit(zone, () -> {
            }));
        }

        Thread close = Thread.ofVirtual().start(session::close);
        waitForCloseStarted(session);
        assertEquals(1, gameplay.memberCount(0, 0));
        assertFalse(repository.updateEntered.await(100, TimeUnit.MILLISECONDS));

        releasePrior.countDown();
        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertEquals(0, gameplay.memberCount(0, 0));
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
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        PlayerRepository players = repository;
        SessionServices services = TestServices.serverServices(auth, resources, gameplay, players);
        Session observer = managedSession(
                services, createPlayer(players, 101L, "alpha1", 0), "user01");
        Session joining = managedSession(
                services, createPlayer(players, 202L, "beta22", 0), "user02");
        assertTrue(gameplay.mapManager().finishLoad(observer));
        GameplayTestSupport.drain(observer);

        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(observer, fullQueue);

        AtomicBoolean joined = new AtomicBoolean();
        Thread finishLoad = Thread.ofVirtual().start(() ->
                joined.set(gameplay.mapManager().finishLoad(joining)));

        assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
        assertTrue(finishLoad.isAlive());
        CountDownLatch nextZoneAction = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(gameplay.findZone(0, 0), nextZoneAction::countDown));
        assertTrue(nextZoneAction.await(5, TimeUnit.SECONDS));
        assertEquals(1, gameplay.memberCount(0, 0));

        repository.allowUpdate.countDown();
        finishLoad.join(1_000);
        assertFalse(finishLoad.isAlive());
        assertTrue(joined.get());
        assertEquals(SessionState.CLOSED, observer.state());
        assertEquals(1, gameplay.memberCount(0, 0));

        joining.close();
    }

    @Test
    void closeKeepsAccountReservedUntilFinalPlayerCheckpointCompletes() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices maps = new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterManager(resources));
        PlayerRepository players = repository;
        Player player = createPlayer(players, 101L, "alpha1", 0);
        player.injure(player.hp() - 77);
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
        PlayerRepository players = repository;
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        GameplayServices gameplay = new GameplayServices(GameResources.unavailable());
        SessionServices services = TestServices.serverServices(
                auth, GameResources.unavailable(), gameplay, players);
        Player moverProfile = createPlayer(players, 101L, "alpha1", 0);
        Player observerProfile = createPlayer(players, 202L, "beta22", 0);
        Session mover = managedSession(services, moverProfile, "user01");
        Session observer = managedSession(services, observerProfile, "user02");
        gameplay.mapManager().finishLoad(mover);
        gameplay.mapManager().finishLoad(observer);
        GameplayTestSupport.drain(mover);
        GameplayTestSupport.drain(observer);

        GameplayTestSupport.BlockingOfferQueue observerQueue =
                new GameplayTestSupport.BlockingOfferQueue();
        GameplayTestSupport.replaceSendQueue(observer, observerQueue);
        AtomicBoolean moved = new AtomicBoolean();
        Thread movement = Thread.ofVirtual().start(() ->
                moved.set(gameplay.movePlayer(mover, 1260, 640)));
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
        PlayerRepository players = repository;
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        SessionServices services = TestServices.serverServices(
                auth, GameResources.unavailable(), gameplay, players);
        Player targetProfile = createPlayer(players, 101L, "alpha1", 0);
        targetProfile.changeMap(1, 0, 1250, 648);
        targetProfile.injure(targetProfile.hp() - 100);
        Player observerProfile = createPlayer(players, 202L, "beta22", 0);
        observerProfile.changeMap(1, 0, 1250, 648);
        Session target = managedSession(services, targetProfile, "user01");
        Session observer = managedSession(services, observerProfile, "user02");
        gameplay.mapManager().finishLoad(target);
        gameplay.mapManager().finishLoad(observer);
        GameplayTestSupport.drain(target);
        GameplayTestSupport.drain(observer);
        assertTrue(gameplay.attackMonster(target, 101));
        GameplayTestSupport.drain(target);
        GameplayTestSupport.drain(observer);
        clock.advanceMillis(1L);

        BlockingAttackQueue observerQueue = new BlockingAttackQueue();
        GameplayTestSupport.replaceSendQueue(observer, observerQueue);
        Thread lifecycle = Thread.ofVirtual().start(
                () -> gameplay.monsterManager().update(gameplay.mapManager()));
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
        PlayerRepository players = repository;
        Player player = createPlayer(players, 101L, "alpha1", 0);
        player.injure(player.hp() - 77);
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AccountAuth(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterManager(GameResources.unavailable())),
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
        PlayerRepository players = repository;
        Player player = createPlayer(players, 101L, "alpha1", 0);
        player.injure(player.hp() - 66);
        SessionManager manager = new SessionManager();
        TestTransport transport = new TestTransport();
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AccountAuth(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterManager(GameResources.unavailable())),
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
        PlayerRepository players = repository;
        Player player = createPlayer(players, 101L, "alpha1", 0);
        SessionManager manager = new SessionManager();
        TestTransport transport = new TestTransport();
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(new AccountAuth(new TestAccountRepository()),
                        GameResources.unavailable(),
                        new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                                new MonsterManager(GameResources.unavailable())),
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

    @Test
    void closeOrdersFinalCheckpointAfterEarlierDeathReturnSave() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, false);
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        ZoneTestHooks.call(owner, () -> session.player().injure(200));
        Object handler = mapHandler(session, services);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread transition = startDeathReturn(handler, failure);
        Thread close = null;

        try {
            assertTrue(repository.firstSaveEntered.await(5, TimeUnit.SECONDS));
            assertSame(owner, session.zone(), "same-Zone death return keeps its writer");
            int latestHp = ZoneTestHooks.call(owner, () -> session.player().injure(10));
            assertEquals(190, latestHp);
            close = startClose(session, failure);
            waitForCloseStarted(session);
            waitForCheckpointWaitOrCompletion(session, owner, close);

            assertTrue(close.isAlive(), "final save must wait for the earlier checkpoint");
            assertEquals(1, repository.checkpointCalls.get());
            assertSame(session, session.manager().findByAccount("user01"));
            assertFalse(owner.hasPlayer(session.player()));
            assertNull(session.zone());
            CountDownLatch writerAvailable = new CountDownLatch(1);
            assertTrue(ZoneTestHooks.submit(owner, writerAvailable::countDown));
            assertTrue(writerAvailable.await(5, TimeUnit.SECONDS));

            repository.allowFirstSave.countDown();
            transition.join(5_000);
            close.join(5_000);

            assertFalse(transition.isAlive());
            assertFalse(close.isAlive());
            assertNull(failure.get());
            assertEquals(List.of(200, 190), repository.completedSaves.stream()
                    .map(PlayerSaveData::hp).toList());
            assertEquals(190, delegate.requireByAccountId(101L).hp());
            assertNull(session.manager().findByAccount("user01"));
        } finally {
            repository.allowFirstSave.countDown();
            transition.join(5_000);
            if (close != null) {
                close.join(5_000);
            }
            session.close();
        }
    }

    @Test
    void checkpointAndCloseRejectZoneWriterReentry() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, false);
        repository.allowFirstSave.countDown();
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        Zone owner = session.zone();
        try {
            ZoneTestHooks.call(owner, () -> {
                PlayerSaveData saved = PlayerSaveData.capture(session.player());
                assertThrows(IllegalStateException.class, () -> session.savePlayer(saved));
                assertThrows(IllegalStateException.class, session::close);
                return null;
            });
            assertEquals(0, repository.checkpointCalls.get());
            assertEquals(SessionState.IN_GAME, session.state());
            assertSame(owner, session.zone());
            assertTrue(owner.hasPlayer(session.player()));
        } finally {
            session.close();
        }
    }

    @Test
    void closedSessionRejectsLateTransitionCheckpoint() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, false);
        repository.allowFirstSave.countDown();
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        ZoneTestHooks.call(owner, () -> session.player().injure(200));
        MapManager.MapChange earlier = gameplay.mapManager().returnHomeFromDeath(session);
        assertNotNull(earlier);
        assertEquals(200, earlier.saveData().hp());
        int latestHp = ZoneTestHooks.call(owner, () -> session.player().injure(10));
        assertEquals(190, latestHp);
        Object handler = mapHandler(session, services);

        session.close();
        Method save = handler.getClass().getDeclaredMethod("save", PlayerSaveData.class);
        save.setAccessible(true);
        save.invoke(handler, earlier.saveData());

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(1, repository.checkpointCalls.get(), "late checkpoint must not reach persistence");
        assertEquals(190, delegate.requireByAccountId(101L).hp());
        assertNull(session.manager().findByAccount("user01"));
    }

    @Test
    void deadDisconnectCheckpointsRevivedHomeAndReloadsAlive() throws Exception {
        TestPlayerRepository repository = new TestPlayerRepository();
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Player initial = createPlayer(repository, 101L, "alpha1", 0);
        Player player = new Player(initial.id(), initial.accountId(), initial.name(), initial.gender(),
                initial.power(), initial.potential(), initial.level(), initial.exp(), initial.baseStats(),
                new Player.CurrentStats(450, 320, 10, 0, 0, 0, 5, 12), 450, 23,
                initial.appearance(), initial.coin(), initial.coinLock(), initial.diamond(), initial.ruby(),
                1, 0, 4464, 936);
        Session session = managedSession(services, player, "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        ZoneTestHooks.call(owner, () -> player.injure(450));
        assertTrue(player.isDead());

        session.close();

        PlayerRecord saved = repository.requireByAccountId(101L);
        assertEquals(0, saved.mapId());
        assertEquals(1250, saved.x());
        assertEquals(648, saved.y());
        assertEquals(450, saved.hp());
        assertEquals(320, saved.mp());
        assertEquals(450, saved.currentStats().maxHp());
        assertEquals(320, saved.currentStats().maxMp());
        Player loaded = services.playerManager().load(101L);
        assertNotNull(loaded);
        assertFalse(loaded.isDead());
        assertEquals(0, loaded.mapId());
        assertEquals(1250, loaded.x());
        assertEquals(648, loaded.y());
        assertEquals(450, loaded.hp());
        assertEquals(320, loaded.mp());
        assertFalse(owner.hasPlayer(session.player()));
        assertNull(session.zone());
        assertNull(session.manager().findByAccount("user01"));
    }

    @Test
    void bootstrapCompletionKeepsAccountReservedUntilFinalCheckpoint() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Player player = createPlayer(repository, 101L, "alpha1", 0);
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, ClientConfig.defaults());
        Session competing = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, ClientConfig.defaults());
        assertTrue(manager.tryAdd(session, 1));
        assertTrue(manager.beginAccountAdmission(session, player.accountId(), "user01"));
        session.bindPlayer(player);
        assertTrue(session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE));
        assertTrue(session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED));
        assertTrue(session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread close = startClose(session, failure);

        try {
            assertTrue(repository.updateEntered.await(5, TimeUnit.SECONDS));
            assertEquals(SessionState.CLOSED, session.state());

            manager.finishAccountAdmission(session, true);

            assertSame(session, manager.findByAccount("user01"),
                    "bootstrap completion must not release an account whose final save is pending");
            assertFalse(manager.beginAccountAdmission(competing, 101L, "user01"));
            assertTrue(close.isAlive());
            repository.allowUpdate.countDown();
            close.join(5_000);

            assertFalse(close.isAlive());
            assertNull(failure.get());
            assertEquals(1, repository.checkpointCalls.get());
            assertEquals(200, delegate.requireByAccountId(101L).hp());
            assertNull(manager.findByAccount("user01"));
            assertEquals(0, manager.onlineCount());
        } finally {
            repository.allowUpdate.countDown();
            close.join(5_000);
            manager.finishAccountAdmission(competing, false);
            competing.close();
            session.close();
        }
    }

    @Test
    void failedFinalCheckpointStillWaitsForEarlierSaveBeforeAccountRelease() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, true);
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        ZoneTestHooks.call(owner, () -> session.player().injure(200));
        Object handler = mapHandler(session, services);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread transition = startDeathReturn(handler, failure);
        Thread close = null;

        try {
            assertTrue(repository.firstSaveEntered.await(5, TimeUnit.SECONDS));
            int latestHp = ZoneTestHooks.call(owner, () -> session.player().injure(10));
            assertEquals(190, latestHp);
            close = startClose(session, failure);
            waitForCloseStarted(session);
            waitForCheckpointWaitOrCompletion(session, owner, close);

            assertTrue(close.isAlive(), "account remains reserved while the earlier save is pending");
            assertSame(session, session.manager().findByAccount("user01"));
            assertEquals(1, repository.checkpointCalls.get());
            assertNull(repository.failedSave.get());

            repository.allowFirstSave.countDown();
            transition.join(5_000);
            close.join(5_000);

            assertFalse(transition.isAlive());
            assertFalse(close.isAlive());
            assertNull(failure.get());
            assertEquals(2, repository.checkpointCalls.get());
            assertNotNull(repository.failedSave.get());
            assertEquals(190, repository.failedSave.get().hp());
            assertEquals(List.of(200), repository.completedSaves.stream()
                    .map(PlayerSaveData::hp).toList());
            assertEquals(200, delegate.requireByAccountId(101L).hp());
            assertNull(session.manager().findByAccount("user01"));
            assertEquals(0, session.manager().onlineCount());
            assertFalse(owner.hasPlayer(session.player()));
            assertNull(session.zone());
        } finally {
            repository.allowFirstSave.countDown();
            transition.join(5_000);
            if (close != null) {
                close.join(5_000);
            }
            session.close();
        }
    }

    @Test
    void closeWaitsForEarlierCheckpointWhenFinalCaptureFails() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, false);
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        PlayerSaveData earlier = ZoneTestHooks.call(owner, () -> PlayerSaveData.capture(session.player()));
        AtomicBoolean savedEarlier = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread save = startCheckpoint(session, earlier, savedEarlier, failure);
        Thread close = null;

        try {
            assertTrue(repository.firstSaveEntered.await(5, TimeUnit.SECONDS));
            int latestHp = ZoneTestHooks.call(owner, () -> session.player().injure(10));
            assertEquals(190, latestHp);
            Method stop = Zone.class.getDeclaredMethod("stopRuntime");
            stop.setAccessible(true);
            stop.invoke(owner);
            close = startClose(session, failure);
            waitForCloseStarted(session);
            waitForCheckpointBlockOrCompletion(close);

            assertTrue(close.isAlive(), "failed final capture still drains the earlier checkpoint");
            assertSame(owner, session.zone(), "stopped writer cannot detach or capture a final save");
            assertTrue(owner.hasPlayer(session.player()));
            assertSame(session, session.manager().findByAccount("user01"));
            assertEquals(1, repository.checkpointCalls.get());

            repository.allowFirstSave.countDown();
            save.join(5_000);
            close.join(5_000);

            assertFalse(save.isAlive());
            assertFalse(close.isAlive());
            assertNull(failure.get());
            assertTrue(savedEarlier.get());
            assertEquals(1, repository.checkpointCalls.get(), "unsafe final capture must not reach DB");
            assertEquals(List.of(200), repository.completedSaves.stream()
                    .map(PlayerSaveData::hp).toList());
            assertEquals(200, delegate.requireByAccountId(101L).hp());
            assertNull(session.manager().findByAccount("user01"));
            assertEquals(0, session.manager().onlineCount());
        } finally {
            repository.allowFirstSave.countDown();
            save.join(5_000);
            if (close != null) {
                close.join(5_000);
            }
            session.close();
        }
    }

    @Test
    void queuedCheckpointRechecksClosedAfterWaitingForEarlierSave() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        OrderedPlayerRepository repository = new OrderedPlayerRepository(delegate, false);
        GameResources resources = mapsWithoutMonsters();
        GameplayServices gameplay = new GameplayServices(resources);
        SessionServices services = TestServices.serverServices(
                new AccountAuth(new TestAccountRepository()), resources, gameplay, repository);
        Session session = managedSession(
                services, createPlayer(repository, 101L, "alpha1", 0), "user01");
        assertTrue(gameplay.mapManager().finishLoad(session));
        GameplayTestSupport.drain(session);
        Zone owner = session.zone();
        PlayerSaveData earlier = ZoneTestHooks.call(owner, () -> PlayerSaveData.capture(session.player()));
        AtomicBoolean savedEarlier = new AtomicBoolean();
        AtomicBoolean savedQueued = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = startCheckpoint(session, earlier, savedEarlier, failure);
        Thread queued = null;
        Thread close = null;

        try {
            assertTrue(repository.firstSaveEntered.await(5, TimeUnit.SECONDS));
            PlayerSaveData waitingSave = ZoneTestHooks.call(owner, () -> {
                session.player().injure(10);
                return PlayerSaveData.capture(session.player());
            });
            queued = startCheckpoint(session, waitingSave, savedQueued, failure);
            waitForCheckpointBlockOrCompletion(queued);
            assertEquals(Thread.State.BLOCKED, queued.getState());
            assertEquals(SessionState.IN_GAME, session.state());
            int latestHp = ZoneTestHooks.call(owner, () -> session.player().injure(20));
            assertEquals(170, latestHp);

            close = startClose(session, failure);
            waitForCloseStarted(session);
            waitForCheckpointWaitOrCompletion(session, owner, close);
            assertTrue(close.isAlive());
            assertSame(session, session.manager().findByAccount("user01"));
            assertEquals(1, repository.checkpointCalls.get());

            repository.allowFirstSave.countDown();
            first.join(5_000);
            queued.join(5_000);
            close.join(5_000);

            assertFalse(first.isAlive());
            assertFalse(queued.isAlive());
            assertFalse(close.isAlive());
            assertNull(failure.get());
            assertTrue(savedEarlier.get());
            assertFalse(savedQueued.get(), "queued ordinary checkpoint must recheck CLOSED inside the lock");
            assertEquals(2, repository.checkpointCalls.get());
            assertEquals(List.of(200, 170), repository.completedSaves.stream()
                    .map(PlayerSaveData::hp).toList());
            assertEquals(170, delegate.requireByAccountId(101L).hp());
            assertNull(session.manager().findByAccount("user01"));
            assertFalse(owner.hasPlayer(session.player()));
            assertNull(session.zone());
        } finally {
            repository.allowFirstSave.countDown();
            first.join(5_000);
            if (queued != null) {
                queued.join(5_000);
            }
            if (close != null) {
                close.join(5_000);
            }
            session.close();
        }
    }

    private static GameResources mapsWithoutMonsters() {
        return new GameResources(null, -1, List.of(), java.util.Map.of(),
                MapTestSupport.canonicalMaps(), List.of(), List.of(), -1,
                List.of(), List.of(), java.util.Map.of());
    }

    private static Object mapHandler(Session session, SessionServices services) throws Exception {
        Class<?> type = Class.forName("com.project.game.network.handler.MapHandler");
        Constructor<?> constructor = type.getDeclaredConstructor(
                Session.class, MapManager.class, GameResources.class);
        constructor.setAccessible(true);
        return constructor.newInstance(session, services.maps(), services.resources());
    }

    private static Thread startDeathReturn(Object handler, AtomicReference<Throwable> failure) throws Exception {
        Method returnHome = handler.getClass().getDeclaredMethod("handleReturnTownFromDie", Message.class);
        returnHome.setAccessible(true);
        return Thread.ofVirtual().start(() -> {
            try {
                returnHome.invoke(handler, new Message(MessageName.RETURN_TOWN_FROM_DIE));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
    }

    private static Thread startClose(Session session, AtomicReference<Throwable> failure) {
        return Thread.ofVirtual().start(() -> {
            try {
                session.close();
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
    }

    private static Thread startCheckpoint(Session session, PlayerSaveData player,
                                          AtomicBoolean saved, AtomicReference<Throwable> failure) {
        return Thread.ofVirtual().start(() -> {
            try {
                saved.set(session.savePlayer(player));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
    }

    private static void waitForCheckpointBlockOrCompletion(Thread checkpoint) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = checkpoint.getState();
            if (state == Thread.State.BLOCKED || state == Thread.State.TERMINATED) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("checkpoint neither completed nor reached the checkpoint lock");
    }

    private static void waitForCheckpointWaitOrCompletion(Session session, Zone owner, Thread close) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (close.getState() == Thread.State.TERMINATED) {
                return;
            }
            if (session.zone() == null && !owner.hasPlayer(session.player())
                    && close.getState() == Thread.State.BLOCKED) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("close neither completed nor reached the checkpoint wait");
    }

    private static final class OrderedPlayerRepository implements PlayerRepository {
        private final TestPlayerRepository delegate;
        private final boolean failFinalSave;
        private final CountDownLatch firstSaveEntered = new CountDownLatch(1);
        private final CountDownLatch allowFirstSave = new CountDownLatch(1);
        private final AtomicInteger checkpointCalls = new AtomicInteger();
        private final List<PlayerSaveData> completedSaves = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<PlayerSaveData> failedSave = new AtomicReference<>();

        private OrderedPlayerRepository(TestPlayerRepository delegate, boolean failFinalSave) {
            this.delegate = delegate;
            this.failFinalSave = failFinalSave;
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
        public void save(PlayerSaveData player) {
            int saveNumber = checkpointCalls.incrementAndGet();
            if (saveNumber == 1) {
                firstSaveEntered.countDown();
                try {
                    if (!allowFirstSave.await(5, TimeUnit.SECONDS)) {
                        throw new PlayerRepositoryException("first checkpoint was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new PlayerRepositoryException("first checkpoint interrupted", exception);
                }
            } else if (failFinalSave) {
                failedSave.set(player);
                throw new PlayerRepositoryException("injected final checkpoint failure");
            }
            delegate.save(player);
            completedSaves.add(player);
        }
    }

    private static final class BlockingPlayerRepository implements PlayerRepository {
        private final TestPlayerRepository delegate;
        private final CountDownLatch updateEntered = new CountDownLatch(1);
        private final CountDownLatch allowUpdate = new CountDownLatch(1);
        private final AtomicBoolean interruptedAtCheckpoint = new AtomicBoolean();
        private final AtomicInteger checkpointCalls = new AtomicInteger();
        private final AtomicReference<Thread> checkpointThread = new AtomicReference<>();
        private final AtomicReference<PlayerSaveData> savedPlayer = new AtomicReference<>();

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
        public void save(PlayerSaveData player) {
            checkpointCalls.incrementAndGet();
            interruptedAtCheckpoint.set(Thread.currentThread().isInterrupted());
            checkpointThread.set(Thread.currentThread());
            savedPlayer.set(player);
            updateEntered.countDown();
            try {
                if (!allowUpdate.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new PlayerRepositoryException("timed out in test");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PlayerRepositoryException("interrupted in test", exception);
            }
            delegate.save(player);
        }
    }

    private static final class RejectDeathQueue extends LinkedBlockingQueue<Message> {
        private final AtomicReference<Thread> writerThread = new AtomicReference<>();

        @Override
        public boolean offer(Message message) {
            if (message.command() == MessageName.MONSTER_START_DIE) {
                writerThread.set(Thread.currentThread());
                return false;
            }
            return super.offer(message);
        }
    }

    private static Session managedSession(
            SessionServices services,
            Player player,
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
