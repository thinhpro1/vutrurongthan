package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.testsupport.TestAccountRepository;
import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.AccountRepository;
import com.project.game.persistence.account.AccountRepositoryException;

import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AuthService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.MessageHandlerTestSupport.*;

class MessageHandlerAuthTest {

    @Test
    void closesWhenLoginOmitsRequiredLoginVersion() throws Exception {
        AuthService auth = registeredAuth();
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
        AuthService auth = new AuthService(repository);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "192.0.2.44");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(new Message(
                MessageName.REGISTER_USER,
                new MessageWriter().writeUtf("user01").writeUtf("secret1").toByteArray()));

        assertEquals("192.0.2.44", repository.requireAccount("user01").ipAddress());
    }

    @Test
    void validLoginBindsThenUpdatesMetadataThenAuthenticates() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
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
    void duplicateOnlineLoginDoesNotUpdateSuccessfulLoginMetadata() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
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
        AuthService auth = new AuthService(repository);
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
    void closeFinishesWhileSuccessfulLoginMetadataUpdateIsBlocked() throws Exception {
        BlockingMetadataRepository repository = new BlockingMetadataRepository();
        AuthService auth = new AuthService(repository);
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
        AuthService auth = new AuthService(repository);
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
        AuthService auth = registeredAuth();
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
        AuthService auth = registeredAuth();
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

    private static AuthService registeredAuth() {
        AuthService auth = TestServices.authService();
        auth.register("user01", "secret1", "127.0.0.1");
        return auth;
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
