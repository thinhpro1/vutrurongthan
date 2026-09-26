package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.testsupport.TestAccountRepository;
import com.project.game.testsupport.TestPlayerRepository;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.AccountRepository;
import com.project.game.persistence.account.AccountRepositoryException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;

import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AccountAuth;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.MessageHandlerTestSupport.*;

class MessageHandlerAuthTest {

    @Test
    void closesWhenLoginOmitsRequiredLoginVersion() throws Exception {
        AccountAuth auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01").writeUtf("secret1");

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void registerUsesServerDerivedRemoteAddress() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "192.0.2.44");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(new Message(
                MessageName.REGISTER_USER,
                new MessageWriter().writeUtf("user01").writeUtf("secret1").toByteArray()));

        assertEquals("192.0.2.44", repository.requireAccount("user01").ipAddress());
    }

    @Test
    void closesWhenRegisterContainsTrailingBytes() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        Message register = new Message(MessageName.REGISTER_USER,
                new MessageWriter().writeUtf("user01").writeUtf("secret1").writeByte(99).toByteArray());

        newHandler(session, auth).onMessage(register);

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, repository.accountCount());
    }

    @Test
    void validLoginBindsThenUpdatesMetadataThenAuthenticates() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(session, manager.findByAccount("user01"));
        assertEquals(1, repository.metadataUpdateCount());
        assertEquals("198.51.100.1", repository.requireAccount("user01").ipAddress());
        assertEquals(MessageName.START_CREATE_PLAYER_SCREEN, drainMessages(session).getFirst().command());
    }

    @Test
    void playerLoadRunsAfterAuthenticationTransition() throws Exception {
        TestAccountRepository accounts = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(accounts);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        TestPlayerRepository delegate = new TestPlayerRepository();
        AtomicReference<Session> observedSession = new AtomicReference<>();
        AtomicReference<SessionState> observedState = new AtomicReference<>();
        PlayerRepository players = new PlayerRepository() {
            @Override
            public Optional<PlayerRecord> findByAccountId(long accountId) {
                observedState.set(observedSession.get().state());
                return delegate.findByAccountId(accountId);
            }

            @Override
            public PlayerRecord create(PlayerRecord initialWithoutId) {
                return delegate.create(initialWithoutId);
            }

            @Override
            public void save(PlayerSaveData player) {
                delegate.save(player);
            }
        };
        GameResources resources = GameResources.fromRoots(null, java.nio.file.Path.of("resources", "json"));
        SessionServices services = TestServices.serverServices(auth, resources,
                new GameplayServices(MapTestSupport.canonicalMaps(), GameResources.unavailable()), players);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, services, 1024, manager, "198.51.100.1");
        observedSession.set(session);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, services, ClientConfig.defaults()).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, observedState.get());
        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(MessageName.START_CREATE_PLAYER_SCREEN, drainMessages(session).getFirst().command());
    }

    @Test
    void closedSessionIsNotBoundAfterBlockedPlayerLoad() throws Exception {
        AccountAuth auth = TestServices.auth();
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        TestPlayerRepository delegate = new TestPlayerRepository();
        delegate.seed(PlayerRecord.fromSaveData(PlayerSaveData.capture(Player.create(1L, "alpha1", 0))));
        BlockingPlayerRepository players = new BlockingPlayerRepository(delegate);
        GameResources resources = GameResources.unavailable();
        SessionServices services = TestServices.serverServices(auth, resources,
                new GameplayServices(MapTestSupport.canonicalMaps(), resources), players);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, services, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        Message loginMessage = loginMessage("user01", "secret1");
        Thread login = Thread.ofVirtual().start(() ->
                newHandler(session, services, ClientConfig.defaults()).onMessage(loginMessage));
        assertTrue(players.findEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            session.close();
            closeFinished.countDown();
        });
        assertTrue(closeFinished.await(1, TimeUnit.SECONDS));
        assertEquals(SessionState.CLOSED, session.state());
        assertTrue(login.isAlive());

        players.allowFind.countDown();
        login.join(1_000);
        close.join(1_000);

        assertFalse(login.isAlive());
        assertFalse(close.isAlive());
        assertNull(session.player());
        assertNull(manager.findByAccount("user01"));
        assertEquals(0, session.queuedMessages());
    }

    @Test
    void playerCreateCommitContendsWithSessionCloseAtomically() throws Exception {
        AccountAuth auth = TestServices.auth();
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository players = new BlockingPlayerRepository(delegate);
        GameResources resources = GameResources.fromRoots(null, java.nio.file.Path.of("resources", "json"));
        SessionServices services = TestServices.serverServices(auth, resources,
                new GameplayServices(MapTestSupport.canonicalMaps(), GameResources.unavailable()), players);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, services, 1024, manager, "198.51.100.1");
        session.bindAccount(1L, "user01");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        AtomicReference<Throwable> createFailure = new AtomicReference<>();
        AtomicReference<Player> playerAtClose = new AtomicReference<>();
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch closeAcquired = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread create = null;
        Thread bootstrapWatcher = null;
        Thread close = null;
        Thread closeWatcher = null;
        try {
            synchronized (session) {
                Message createMessage = new Message(MessageName.CREATE_PLAYER,
                        new MessageWriter().writeUtf("alpha1").writeByte(0).toByteArray());
                create = Thread.ofVirtual().start(() -> {
                    try {
                        newHandler(session, services, ClientConfig.defaults()).onMessage(createMessage);
                    } catch (Throwable failure) {
                        createFailure.set(failure);
                    }
                });
                assertTrue(players.createEntered.await(1, TimeUnit.SECONDS));
                CountDownLatch bootstrapBlocked = new CountDownLatch(1);
                bootstrapWatcher = watchForPlayerBootstrapBlock(create, bootstrapBlocked);

                players.allowCreate.countDown();
                assertTrue(players.createReturned.await(1, TimeUnit.SECONDS));
                assertTrue(bootstrapBlocked.await(1, TimeUnit.SECONDS),
                        "bootstrap did not contend on the Session monitor");
                assertNull(session.player());

                close = Thread.ofVirtual().start(() -> {
                    closeStarted.countDown();
                    synchronized (session) {
                        playerAtClose.set(session.player());
                        closeAcquired.countDown();
                        session.close();
                    }
                    closeFinished.countDown();
                });
                CountDownLatch closeBlocked = new CountDownLatch(1);
                closeWatcher = watchForBlocked(close, closeBlocked);
                assertTrue(closeStarted.await(1, TimeUnit.SECONDS));
                assertTrue(closeBlocked.await(1, TimeUnit.SECONDS));
            }
        } finally {
            players.allowCreate.countDown();
        }

        create.join(1_000);
        close.join(1_000);
        bootstrapWatcher.join(1_000);
        closeWatcher.join(1_000);
        assertTrue(closeAcquired.await(1, TimeUnit.SECONDS));
        assertTrue(closeFinished.await(1, TimeUnit.SECONDS));
        assertFalse(create.isAlive());
        assertFalse(close.isAlive());
        assertNull(createFailure.get());
        assertEquals(SessionState.CLOSED, session.state());

        Player observedByClose = playerAtClose.get();
        if (observedByClose == null) {
            assertNull(session.player());
        } else {
            assertSame(observedByClose, session.player());
        }
    }

    @Test
    void closedSessionIsNotBoundAfterBlockedPlayerCreate() throws Exception {
        AccountAuth auth = TestServices.auth();
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        TestPlayerRepository delegate = new TestPlayerRepository();
        BlockingPlayerRepository players = new BlockingPlayerRepository(delegate);
        GameResources resources = GameResources.unavailable();
        SessionServices services = TestServices.serverServices(auth, resources,
                new GameplayServices(MapTestSupport.canonicalMaps(), resources), players);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, services, 1024, manager, "198.51.100.1");
        session.bindAccount(1L, "user01");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        Message create = new Message(MessageName.CREATE_PLAYER,
                new MessageWriter().writeUtf("alpha1").writeByte(0).toByteArray());
        Thread createThread = Thread.ofVirtual().start(() ->
                newHandler(session, services, ClientConfig.defaults()).onMessage(create));
        assertTrue(players.createEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            session.close();
            closeFinished.countDown();
        });
        assertTrue(closeFinished.await(1, TimeUnit.SECONDS));
        assertEquals(SessionState.CLOSED, session.state());
        assertTrue(createThread.isAlive());

        players.allowCreate.countDown();
        createThread.join(1_000);
        close.join(1_000);

        assertFalse(createThread.isAlive());
        assertFalse(close.isAlive());
        assertNull(session.player());
        assertEquals(1, delegate.requireByAccountId(1L).id());
        assertEquals(0, session.queuedMessages());
    }

    @Test
    void duplicateOnlineLoginDoesNotUpdateSuccessfulLoginMetadata() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session first = newSession(auth, 1024, manager, "198.51.100.1");
        Session second = newSession(auth, 1024, manager, "198.51.100.2");
        first.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        second.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(first, auth).onMessage(loginMessage("user01", "secret1"));
        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, first.state());
        assertEquals(SessionState.HANDSHAKE_DONE, second.state());
        assertEquals(first, manager.findByAccount("user01"));
        assertEquals(1, repository.metadataUpdateCount());
        assertEquals("198.51.100.1", repository.requireAccount("user01").ipAddress());
        Message dialog = drainMessages(second).getFirst();
        assertEquals(MessageName.DIALOG_OK, dialog.command());
        assertEquals("Tài khoản đang đăng nhập ở thiết bị khác", dialog.reader().readUtf());
    }

    @Test
    void metadataFailureRollsBackAccountBindingAndStaysUnauthenticated() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        repository.failUpdate(true);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(null, session.player());
        Message dialog = drainMessages(session).getFirst();
        assertEquals(MessageName.DIALOG_OK, dialog.command());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", dialog.reader().readUtf());
    }

    @Test
    void invalidPersistedPlayerConversionIsReportedAsSystemBusy() throws Exception {
        AccountAuth auth = TestServices.auth();
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        TestPlayerRepository players = (TestPlayerRepository) TestServices.playerRepositoryFor(auth);
        Player valid = Player.create(1L, "alpha1", 0);
        PlayerSaveData saved = PlayerSaveData.capture(valid);
        PlayerRecord record = PlayerRecord.fromSaveData(saved);
        players.seed(new PlayerRecord(
                record.id(), record.accountId(), record.name(), 99, record.power(), record.potential(),
                record.level(), record.exp(), record.baseStats(), record.currentStats(), record.hp(), record.mp(),
                record.appearance(), record.coin(), record.coinLock(), record.diamond(), record.ruby(),
                record.mapId(), record.x(), record.y()));

        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(null, session.player());
        assertEquals(null, manager.findByAccount("user01"));
        Message dialog = drainMessages(session).getFirst();
        assertEquals(MessageName.DIALOG_OK, dialog.command());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", dialog.reader().readUtf());
    }

    @Test
    void closeFinishesWhileSuccessfulLoginMetadataUpdateIsBlocked() throws Exception {
        BlockingMetadataRepository repository = new BlockingMetadataRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        Thread login = Thread.ofVirtual().start(() -> {
            try {
                newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        assertTrue(repository.updateEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeInvoked = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            closeInvoked.countDown();
            session.close();
            closeFinished.countDown();
        });
        assertTrue(closeInvoked.await(1, TimeUnit.SECONDS));
        try {
            assertTrue(closeFinished.await(1, TimeUnit.SECONDS),
                    "session close waited for the metadata repository call");
            assertEquals(SessionState.CLOSED, session.state());
            assertSame(session, manager.findByAccount("user01"));
        } finally {
            repository.allowUpdate.countDown();
        }
        login.join(1_000);
        close.join(1_000);

        assertFalse(login.isAlive());
        assertFalse(close.isAlive());
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(1, repository.delegate.metadataUpdateCount());
    }

    @Test
    void reconnectWaitsForClosedSessionLoginAdmissionToFinish() throws Exception {
        BlockingMetadataRepository repository = new BlockingMetadataRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session first = newSession(auth, 1024, manager, "198.51.100.1");
        Session second = newSession(auth, 1024, manager, "198.51.100.2");
        first.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        second.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        Thread firstLogin = Thread.ofVirtual().start(() -> {
            try {
                newHandler(first, auth).onMessage(loginMessage("user01", "secret1"));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        assertTrue(repository.updateEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            first.close();
            closeFinished.countDown();
        });
        assertTrue(closeFinished.await(1, TimeUnit.SECONDS));
        assertEquals(SessionState.CLOSED, first.state());
        assertSame(first, manager.findByAccount("user01"));

        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.HANDSHAKE_DONE, second.state());
        assertSame(first, manager.findByAccount("user01"));
        assertEquals(0, repository.delegate.metadataUpdateCount());

        repository.allowUpdate.countDown();
        firstLogin.join(1_000);
        close.join(1_000);

        assertFalse(firstLogin.isAlive());
        assertFalse(close.isAlive());
        assertEquals(SessionState.CLOSED, first.state());
        assertEquals(null, manager.findByAccount("user01"));

        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, second.state());
        assertSame(second, manager.findByAccount("user01"));
        assertEquals(2, repository.delegate.metadataUpdateCount());
        assertEquals("198.51.100.2", repository.delegate.requireAccount("user01").ipAddress());
    }

    @Test
    void closesWhenLoginContainsTrailingBytes() throws Exception {
        AccountAuth auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01")
                .writeUtf("secret1").writeByte(1).writeByte(99);

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenCreatePlayerContainsTrailingBytes() throws Exception {
        AccountAuth auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.bindAccount(1L, "user01");
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter create = new MessageWriter()
                .writeUtf("alpha1")
                .writeByte(0)
                .writeByte(99);

        handler.onMessage(new Message(MessageName.CREATE_PLAYER, create.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    private static AccountAuth registeredAuth() {
        AccountAuth auth = TestServices.auth();
        auth.register("user01", "secret1", "127.0.0.1");
        return auth;
    }

    private static Thread watchForPlayerBootstrapBlock(Thread target, CountDownLatch blocked) {
        return Thread.ofPlatform().start(() -> {
            while (target.isAlive()) {
                if (target.getState() == Thread.State.BLOCKED && hasPlayerBootstrapFrame(target)) {
                    blocked.countDown();
                    return;
                }
                Thread.onSpinWait();
            }
        });
    }

    private static Thread watchForBlocked(Thread target, CountDownLatch blocked) {
        return Thread.ofPlatform().start(() -> {
            while (target.isAlive()) {
                if (target.getState() == Thread.State.BLOCKED) {
                    blocked.countDown();
                    return;
                }
                Thread.onSpinWait();
            }
        });
    }

    private static boolean hasPlayerBootstrapFrame(Thread target) {
        for (StackTraceElement frame : target.getStackTrace()) {
            if (frame.getClassName().equals("com.project.game.network.handler.PlayerHandler")
                    && (frame.getMethodName().equals("openPlayerForAuthenticatedAccount")
                    || frame.getMethodName().equals("handleCreatePlayer"))) {
                return true;
            }
        }
        return false;
    }

    private static final class BlockingPlayerRepository implements PlayerRepository {
        private final TestPlayerRepository delegate;
        private final CountDownLatch findEntered = new CountDownLatch(1);
        private final CountDownLatch allowFind = new CountDownLatch(1);
        private final CountDownLatch createEntered = new CountDownLatch(1);
        private final CountDownLatch allowCreate = new CountDownLatch(1);
        private final CountDownLatch createReturned = new CountDownLatch(1);

        private BlockingPlayerRepository(TestPlayerRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PlayerRecord> findByAccountId(long accountId) {
            findEntered.countDown();
            await(allowFind, "find");
            return delegate.findByAccountId(accountId);
        }

        @Override
        public PlayerRecord create(PlayerRecord initialWithoutId) {
            createEntered.countDown();
            await(allowCreate, "create");
            try {
                return delegate.create(initialWithoutId);
            } finally {
                createReturned.countDown();
            }
        }

        @Override
        public void save(PlayerSaveData player) {
            delegate.save(player);
        }

        private static void await(CountDownLatch latch, String operation) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for test " + operation + " release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for test " + operation, exception);
            }
        }
    }

    private static final class BlockingMetadataRepository implements AccountRepository {
        private final TestAccountRepository delegate = new TestAccountRepository();
        private final CountDownLatch updateEntered = new CountDownLatch(1);
        private final CountDownLatch allowUpdate = new CountDownLatch(1);

        @Override
        public Optional<AccountRecord> findByUsername(String username) {
            return delegate.findByUsername(username);
        }

        @Override
        public long create(String username, byte[] passwordHash, byte[] passwordSalt, String ipAddress) {
            return delegate.create(username, passwordHash, passwordSalt, ipAddress);
        }

        @Override
        public void updateSuccessfulLogin(long accountId, String ipAddress, Instant loginAt) {
            updateEntered.countDown();
            try {
                if (!allowUpdate.await(5, TimeUnit.SECONDS)) {
                    throw new AccountRepositoryException("timed out waiting for test release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AccountRepositoryException("interrupted while waiting for test release", exception);
            }
            delegate.updateSuccessfulLogin(accountId, ipAddress, loginAt);
        }
    }
}
