package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHandlerTest {
    @Test
    void closesAfterThreeUnsupportedCommandsInGame() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        MessageHandler handler = newHandler(session, new AuthService());

        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void finishLoadMapIsAcceptedInGameWithoutConsumingViolationBudget() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        MessageHandler handler = newHandler(session, new AuthService());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
    }

    @Test
    void finishLoadMapRejectsTrailingBytes() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        MessageHandler handler = newHandler(session, new AuthService());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
    }

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
    void closesWhenUpdateDataContainsTrailingBytes() {
        AuthService auth = new AuthService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(new Message(MessageName.UPDATE_DATA, new byte[]{-1, 123}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenCreatePlayerContainsTrailingBytes() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.bindAccount("user01");
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter create = new MessageWriter()
                .writeUtf("alpha1")
                .writeByte(0)
                .writeByte(99);

        handler.onMessage(new Message(MessageName.CREATE_PLAYER, create.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void doesNotSendEmptyFrameDatasetWhenFrameResourcesAreUnavailable() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{7}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void serializesExactLegacyLevelResource() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    new ServerServices(new AuthService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();

                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA,
                        new MessageWriter().writeByte(6).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                assertEquals(MessageName.UPDATE_DATA, response.command());
                var reader = response.reader();
                assertEquals(6, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(102, reader.readUnsignedShort());
                for (int id = 0; id < 102; id++) {
                    assertEquals(id, reader.readShort());
                    reader.readUtf();
                    reader.readLong();
                }
                assertEquals(0, reader.remaining());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void requestIconIsAllowedOnlyAfterHandshake() {
        ResourceService resources = ResourceService.unavailable();

        Session connected = newSession(new AuthService());
        newHandler(connected, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CONNECTED, connected.state());

        Session handshakeDone = newSession(new AuthService());
        handshakeDone.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        newHandler(handshakeDone, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.HANDSHAKE_DONE, handshakeDone.state());

        Session authenticated = newSession(new AuthService());
        authenticated.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        authenticated.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        newHandler(authenticated, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.AUTHENTICATED, authenticated.state());

        Session inGame = newSession(new AuthService());
        inGame.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        inGame.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        inGame.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        newHandler(inGame, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.IN_GAME, inGame.state());

        Session closed = newSession(new AuthService());
        closed.close();
        newHandler(closed, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CLOSED, closed.state());
    }

    @Test
    void parsesRequestIconIdAndQueuesAvailableIcon(@TempDir Path root) throws IOException {
        Files.write(root.resolve("5.png"), new byte[]{1, 2, 3});
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.fromIconRoot(root)).onMessage(iconRequest(5));

        assertEquals(1, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void rejectsRequestIconTrailingBytes() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.REQUEST_ICON, new byte[]{0, 5, 0x7f}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void missingIconDoesNotCloseAuthenticatedSessionOrQueueResponse() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        MessageHandler handler = newHandler(session, ResourceService.unavailable());
        handler.onMessage(iconRequest(5));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(0, session.queuedMessages());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.AUTHENTICATED, session.state());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.AUTHENTICATED, session.state());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void oversizedIconIsNotQueuedPastConfiguredPacketLimit(@TempDir Path root) throws IOException {
        Files.write(root.resolve("5.png"), new byte[70_000]);
        Session session = newSession(new AuthService(), 9);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.fromIconRoot(root))
                .onMessage(iconRequest(5));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(0, session.queuedMessages());
    }

    private static MessageHandler newHandler(Session session, AuthService authService) {
        return newHandler(session, new ServerServices(authService, ResourceService.unavailable()),
                NetworkConfig.defaults());
    }

    private static MessageHandler newHandler(Session session, ResourceService resources) {
        return newHandler(session, new ServerServices(new AuthService(), resources), NetworkConfig.defaults());
    }

    private static MessageHandler newHandler(Session session, ServerServices services, NetworkConfig config) {
        return new MessageHandler(session, services, config, NetworkEventObserver.NO_OP);
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
                new LegacyPacketCodec(maxPacketSize), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                new ServerServices(authService, ResourceService.unavailable()), NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP);
    }

    private static void waitForOutput(ByteArrayOutputStream output) throws InterruptedException {
        int lastSize = -1;
        int stableChecks = 0;
        for (int attempt = 0; attempt < 200; attempt++) {
            int size = output.size();
            if (size > 0 && size == lastSize) {
                stableChecks++;
                if (stableChecks >= 3) {
                    return;
                }
            } else {
                stableChecks = 0;
            }
            lastSize = size;
            Thread.sleep(5);
        }
        if (output.size() > 0) {
            return;
        }
        assertTrue(output.size() > 0, "timed out waiting for level resource response");
    }
}
