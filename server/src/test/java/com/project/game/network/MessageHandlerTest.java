package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.service.AuthService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageHandlerTest {
    @Test
    void closesAfterThreeUnsupportedCommandsInGame() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        MessageHandler handler = new MessageHandler(session, new AuthService());

        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenLoginOmitsRequiredLoginVersion() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = new MessageHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01").writeUtf("secret1");

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenLoginContainsTrailingBytes() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = new MessageHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01")
                .writeUtf("secret1").writeByte(1).writeByte(99);

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenUpdateDataContainsTrailingBytes() {
        AuthService auth = new AuthService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = new MessageHandler(session, auth);

        handler.onMessage(new Message(MessageName.UPDATE_DATA, new byte[]{-1, 123}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void requestIconIsAllowedOnlyAfterHandshake() {
        AtomicInteger requested = new AtomicInteger();
        IconResourceProvider provider = iconId -> {
            requested.incrementAndGet();
            return Optional.empty();
        };

        Session connected = newSession(new AuthService());
        newHandler(connected, provider).onMessage(iconRequest(5));
        assertEquals(SessionState.CONNECTED, connected.state());

        Session handshakeDone = newSession(new AuthService());
        handshakeDone.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        newHandler(handshakeDone, provider).onMessage(iconRequest(5));
        assertEquals(SessionState.HANDSHAKE_DONE, handshakeDone.state());

        Session authenticated = newSession(new AuthService());
        authenticated.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        authenticated.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        newHandler(authenticated, provider).onMessage(iconRequest(5));
        assertEquals(SessionState.AUTHENTICATED, authenticated.state());

        Session inGame = newSession(new AuthService());
        inGame.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        inGame.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        inGame.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        newHandler(inGame, provider).onMessage(iconRequest(5));
        assertEquals(SessionState.IN_GAME, inGame.state());

        Session closed = newSession(new AuthService());
        closed.close();
        newHandler(closed, provider).onMessage(iconRequest(5));
        assertEquals(SessionState.CLOSED, closed.state());
        assertEquals(3, requested.get());
    }

    @Test
    void parsesRequestIconIdAndCallsProviderOnce() {
        AtomicInteger requestedId = new AtomicInteger();
        IconResourceProvider provider = iconId -> {
            requestedId.incrementAndGet();
            assertEquals(5, iconId);
            return Optional.empty();
        };
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, provider).onMessage(iconRequest(5));

        assertEquals(1, requestedId.get());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void rejectsRequestIconTrailingBytes() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, iconId -> Optional.empty()).onMessage(
                new Message(MessageName.REQUEST_ICON, new byte[]{0, 5, 0x7f}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void missingIconDoesNotCloseAuthenticatedSessionOrQueueResponse() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        newHandler(session, iconId -> Optional.empty()).onMessage(iconRequest(5));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(0, session.queuedMessages());
        assertFalse(session.recordProtocolViolation());
        assertFalse(session.recordProtocolViolation());
    }

    @Test
    void oversizedIconIsNotQueuedPastConfiguredPacketLimit() {
        Session session = newSession(new AuthService(), 9);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, iconId -> Optional.of(new byte[]{1, 2, 3, 4}))
                .onMessage(iconRequest(5));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(0, session.queuedMessages());
    }

    private static MessageHandler newHandler(Session session, IconResourceProvider provider) {
        return new MessageHandler(session, new AuthService(), NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP, provider);
    }

    private static Message iconRequest(int iconId) {
        return new Message(MessageName.REQUEST_ICON,
                new MessageWriter().writeShort(iconId).toByteArray());
    }

    private static AuthService registeredAuth() {
        AuthService auth = new AuthService();
        auth.register("user01", "secret1");
        return auth;
    }

    private static Session newSession(AuthService authService) {
        return newSession(authService, 1024);
    }

    private static Session newSession(AuthService authService, int maxPacketSize) {
        SessionManager manager = new SessionManager();
        return new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(maxPacketSize), "abc".getBytes(StandardCharsets.US_ASCII), 4, authService);
    }
}
