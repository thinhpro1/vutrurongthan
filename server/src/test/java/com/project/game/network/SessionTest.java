package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import com.project.game.service.ServerServices;
import com.project.game.account.AuthService;
import com.project.game.player.PlayerService;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.PlayerProfile;
import com.project.game.testsupport.GameplayServices;
import com.project.game.monster.MonsterRuntimeFactory;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
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
                TestServices.serverServices(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
                    TestServices.serverServices(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
    void closeKeepsAccountReservedUntilFinalPlayerCheckpointCompletes() throws Exception {
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository repository = new BlockingPlayerRepository(delegate);
        AuthService auth = new AuthService(new TestAccountRepository());
        GameResources resources = GameResources.unavailable();
        GameplayServices maps = new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        PlayerService players = new PlayerService(repository);
        PlayerProfile player = players.create(101L, "alpha1", 0).player().withHp(77);
        ServerServices services = TestServices.serverServices(auth, resources, maps, players);
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
                                new MonsterRuntimeFactory(GameResources.unavailable())),
                        players),
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
                                new MonsterRuntimeFactory(GameResources.unavailable())),
                        players),
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
                                new MonsterRuntimeFactory(GameResources.unavailable())),
                        players),
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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

    private static void waitForClosed(Session session) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (session.state() == SessionState.CLOSED) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(SessionState.CLOSED, session.state(), "timed out waiting for session close");
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
