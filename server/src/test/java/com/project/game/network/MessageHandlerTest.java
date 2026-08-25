package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.service.AuthService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static AuthService registeredAuth() {
        AuthService auth = new AuthService();
        auth.register("user01", "secret1");
        return auth;
    }

    private static Session newSession(AuthService authService) {
        SessionManager manager = new SessionManager();
        return new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4, authService);
    }
}
