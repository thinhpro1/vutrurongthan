package com.project.game.network;

import com.project.game.testsupport.TestServices;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AuthService;
import com.project.game.resource.IconFingerprint;
import com.project.game.resource.GameResources;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.MessageHandlerTestSupport.*;

class MessageHandlerResourceTest {

    @Test
    void closesWhenUpdateDataContainsTrailingBytes() {
        AuthService auth = TestServices.authService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(new Message(MessageName.UPDATE_DATA, new byte[]{-1, 123}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void doesNotSendEmptyFrameDatasetWhenFrameResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{7}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void serializesExactLegacyLevelResource() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
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
    void serializesMovementEffectResourceInUnityFieldOrder() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(
                    manager.nextId(),
                    new TestTransport(input, output, "127.0.0.1"),
                    manager,
                    new LegacyPacketCodec(262_144),
                    key,
                    4,
                    TestServices.serverServices(TestServices.authService(), resources),
                    NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();

                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA,
                        new MessageWriter().writeByte(3).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()),
                        new LegacyCipher(key),
                        true);

                assertEquals(MessageName.UPDATE_DATA, response.command());
                var reader = response.reader();
                assertEquals(3, reader.readByte());
                assertEquals(2, reader.readByte());
                assertEquals(4, reader.readUnsignedShort());
                for (var expected : resources.effects()) {
                    assertEquals(expected.id(), reader.readShort());
                    assertEquals(expected.dx(), reader.readShort());
                    assertEquals(expected.dy(), reader.readShort());
                    assertEquals(expected.delay(), reader.readShort());
                    assertEquals(expected.icons().size(), reader.readUnsignedByte());
                    for (int iconId : expected.icons()) {
                        assertEquals(iconId, reader.readShort());
                    }
                }
                assertEquals(0, reader.readUnsignedShort());
                assertEquals(0, reader.remaining());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void serializesExactLegacyMonsterResourceInUnityFieldOrder() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(4).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                assertEquals(MessageName.UPDATE_DATA, response.command());
                var reader = response.reader();
                assertEquals(4, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(1, reader.readShort());
                assertEquals(0, reader.readShort());
                assertFalse(reader.readBoolean());
                assertEquals(3, reader.readByte());
                assertEquals(2198, reader.readShort());
                assertEquals(2199, reader.readShort());
                assertEquals(2200, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(30, reader.readShort());
                assertEquals(3, reader.readByte());
                assertEquals(2190, reader.readShort());
                assertEquals(2191, reader.readShort());
                assertEquals(2192, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(30, reader.readShort());
                assertEquals(5, reader.readByte());
                assertEquals(2193, reader.readShort());
                assertEquals(2194, reader.readShort());
                assertEquals(2195, reader.readShort());
                assertEquals(2196, reader.readShort());
                assertEquals(2197, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(20, reader.readShort());
                assertEquals(1, reader.readShort());
                assertEquals(1, reader.readShort());
                assertEquals("Hổ nanh kiếm", reader.readUtf());
                assertEquals(100, reader.readShort());
                assertEquals(1, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(5, reader.readByte());
                assertEquals(11818, reader.readShort());
                assertEquals(11819, reader.readShort());
                assertEquals(11820, reader.readShort());
                assertEquals(11821, reader.readShort());
                assertEquals(11822, reader.readShort());
                assertEquals(11824, reader.readShort());
                assertEquals(11823, reader.readShort());
                assertEquals(175, reader.readShort());
                assertEquals(95, reader.readShort());
                assertEquals(0, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(0, reader.remaining());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void doesNotSendEmptyMonsterDatasetWhenMonsterResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.unavailable()).onMessage(new Message(
                MessageName.UPDATE_DATA, new byte[]{4}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void manifestAdvertisesLoadedEffectVersionTwo() throws Exception {
        assertEquals(2, readManifestEffectVersion(GameResources.fromFrameRoot(
                Path.of("resources", "json"))));
    }

    @Test
    void manifestAdvertisesLoadedMonsterVersionOne() throws Exception {
        assertEquals(1, readManifestMonsterVersion(GameResources.fromFrameRoot(
                Path.of("resources", "json"))));
    }

    @Test
    void manifestAdvertisesUnavailableMonsterVersionMinusOne() throws Exception {
        assertEquals(-1, readManifestMonsterVersion(GameResources.unavailable()));
    }

    @Test
    void doesNotSendEmptyEffectDatasetWhenEffectResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{3}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void requestIconIsAllowedOnlyAfterHandshake() {
        GameResources resources = GameResources.unavailable();

        Session connected = newSession(TestServices.authService());
        newHandler(connected, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CONNECTED, connected.state());

        Session handshakeDone = newSession(TestServices.authService());
        handshakeDone.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        newHandler(handshakeDone, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.HANDSHAKE_DONE, handshakeDone.state());

        Session authenticated = newSession(TestServices.authService());
        authenticated.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        authenticated.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        newHandler(authenticated, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.AUTHENTICATED, authenticated.state());

        Session inGame = newSession(TestServices.authService());
        inGame.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        inGame.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        inGame.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        newHandler(inGame, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.IN_GAME, inGame.state());

        Session closed = newSession(TestServices.authService());
        closed.close();
        newHandler(closed, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CLOSED, closed.state());
    }

    @Test
    void parsesRequestIconIdAndQueuesAvailableIcon(@TempDir Path root) throws IOException {
        Files.write(root.resolve("5.png"), new byte[]{1, 2, 3});
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.fromIconRoot(root)).onMessage(iconRequest(5));

        assertEquals(1, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void rejectsRequestIconTrailingBytes() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.unavailable()).onMessage(
                new Message(MessageName.REQUEST_ICON, new byte[]{0, 5, 0x7f}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void missingIconDoesNotCloseAuthenticatedSessionOrQueueResponse() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        MessageHandler handler = newHandler(session, GameResources.unavailable());
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
        Session session = newSession(TestServices.authService(), 9);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.fromIconRoot(root))
                .onMessage(iconRequest(5));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(0, session.queuedMessages());
    }

    @Test
    void oversizedIconManifestClosesSession(@TempDir Path root) throws IOException {
        Files.write(root.resolve("2.png"), new byte[]{1});
        Files.write(root.resolve("10.png"), new byte[]{2});
        Session session = newSession(TestServices.authService(), 9);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, GameResources.fromIconRoot(root)).onMessage(
                new Message(MessageName.UPDATE_DATA,
                        new MessageWriter().writeByte(12).toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, session.queuedMessages());
    }

    @Test
    void serializesSortedPerIconManifest() throws Exception {
        Path iconRoot = Files.createTempDirectory("icon-manifest-test");
        try {
            byte[] icon2 = new byte[]{4, 5, 6};
            byte[] icon10 = new byte[]{1, 2, 3};
            Files.write(iconRoot.resolve("10.png"), icon10);
            Files.write(iconRoot.resolve("2.png"), icon2);
            GameResources resources = GameResources.fromIconRoot(iconRoot, 2);

            PipedInputStream input = new PipedInputStream();
            try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                SessionManager manager = new SessionManager();
                byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
                Session session = new Session(
                        manager.nextId(),
                        new TestTransport(input, output, "127.0.0.1"),
                        manager,
                        new LegacyPacketCodec(262_144),
                        key,
                        4,
                        TestServices.serverServices(TestServices.authService(), resources),
                        NetworkConfig.defaults(),
                        NetworkEventObserver.NO_OP);
                try {
                    session.start();
                    session.completeHandshake();
                    output.reset();

                    newHandler(session, resources).onMessage(new Message(
                            MessageName.UPDATE_DATA,
                            new MessageWriter().writeByte(12).toByteArray()));

                    waitForOutput(output);
                    Message response = new LegacyPacketCodec(262_144).readServerResponse(
                            new ByteArrayInputStream(output.toByteArray()),
                            new LegacyCipher(key),
                            true);
                    assertEquals(MessageName.UPDATE_DATA, response.command());
                    var reader = response.reader();
                    assertEquals(12, reader.readByte());
                    assertEquals(2, reader.readUnsignedShort());
                    assertEquals(2, reader.readShort());
                    assertEquals(IconFingerprint.fingerprint64(icon2), reader.readLong());
                    assertEquals(10, reader.readShort());
                    assertEquals(IconFingerprint.fingerprint64(icon10), reader.readLong());
                    assertEquals(0, reader.remaining());
                } finally {
                    session.close();
                }
            }
        } finally {
            Files.deleteIfExists(iconRoot.resolve("2.png"));
            Files.deleteIfExists(iconRoot.resolve("10.png"));
            Files.deleteIfExists(iconRoot);
        }
    }

    private static Message iconRequest(int iconId) {
        return new Message(MessageName.REQUEST_ICON,
                new MessageWriter().writeShort(iconId).toByteArray());
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

    private static int readManifestMonsterVersion(GameResources resources) throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                var reader = response.reader();
                assertEquals(-1, reader.readByte());
                for (int index = 0; index < 5; index++) {
                    reader.readByte();
                }
                int monsterVersion = reader.readByte();
                for (int index = 0; index < 7; index++) {
                    reader.readByte();
                }
                assertEquals(0, reader.remaining());
                return monsterVersion;
            } finally {
                session.close();
            }
        }
    }

    private static int readManifestEffectVersion(GameResources resources) throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                var reader = response.reader();
                assertEquals(-1, reader.readByte());
                for (int index = 0; index < 4; index++) {
                    reader.readByte();
                }
                int effectVersion = reader.readByte();
                for (int index = 0; index < 8; index++) {
                    reader.readByte();
                }
                assertEquals(0, reader.remaining());
                return effectVersion;
            } finally {
                session.close();
            }
        }
    }
}
