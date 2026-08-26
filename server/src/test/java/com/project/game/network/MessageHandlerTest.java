package com.project.game.network;

import com.project.game.map.MapService;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import com.project.game.player.PlayerProfile;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHandlerTest {
    @Test
    void tracksMapTemplatesPerSession() {
        Session session = newSession(new AuthService());

        assertFalse(session.hasSentMapTemplate(0));
        session.markMapTemplateSent(0);
        assertTrue(session.hasSentMapTemplate(0));
        assertFalse(session.hasSentMapTemplate(1));
    }

    @Test
    void changesMapOnlyWhenInsideSupportedWaypoint() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(new PlayerPacketWriter());
        ServerServices services = new ServerServices(new AuthService(), resources, maps);
        PlayerProfile start = PlayerProfile.initial("user01", 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());
        session.markMapTemplateSent(0);

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(new PlayerProfile(
                "user01", 7, "alpha1", 0,
                1, 1, 1, 1, 5, 6, -1, -1, -1, -1,
                10, 5, 5, 5, 10, 10, 10, 10,
                150, 150, 100, 100, 12, 0, 0, 1,
                "0%", "0%", "0%", "0%", "0%", "0%",
                10, 0, 10_000, 0, 25, 0, 1, 0, 90, 1008), session.player());
        assertEquals(1, session.queuedMessages());
        Message mapInfo = drainMessages(session).getFirst();
        assertEquals(MessageName.MAP_INFO, mapInfo.command());
        var reader = mapInfo.reader();
        assertEquals(1, reader.readShort());
        assertEquals(1, reader.readShort());
        assertEquals("Bờ sông Pu", reader.readUtf());
        assertEquals(20, reader.readShort());
        assertEquals(62, reader.readShort());
        assertEquals(1240, reader.readUtf().length());
        for (int i = 0; i < 3; i++) {
            reader.readShort();
        }
        for (int i = 0; i < 12; i++) {
            reader.readShort();
        }
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readByte());
        assertEquals(90, reader.readShort());
        assertEquals(1008, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readShort());
        assertEquals(1008, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals("Núi Paozu", reader.readUtf());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(6, reader.readUnsignedByte());
        for (int index = 0; index < 6; index++) {
            reader.readByte();
            reader.readShort();
            reader.readInt();
            reader.readShort();
            reader.readByte();
            reader.readShort();
            reader.readShort();
            reader.readLong();
            reader.readLong();
            reader.readByte();
        }
        assertEquals(0, reader.readUnsignedShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void requestChangeMapOutsideWaypointIsNoOp() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(new PlayerPacketWriter());
        ServerServices services = new ServerServices(new AuthService(), resources, maps);
        PlayerProfile start = PlayerProfile.initial("user01", 7, "alpha1", 0)
                .withLocation(0, 0, 1250, 648);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(start, session.player());
        assertEquals(0, session.queuedMessages());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void requestChangeMapRejectsNonEmptyPayload() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        Session session = inGameSession(new ServerServices(new AuthService(), resources),
                PlayerProfile.initial("user01", 7, "alpha1", 0));

        newHandler(session, resources).onMessage(new Message(
                MessageName.REQUEST_CHANGE_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void mapInfoRevisitUsesCachedTemplateLayout() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        Session session = inGameSession(new ServerServices(new AuthService(), resources),
                PlayerProfile.initial("user01", 7, "alpha1", 0)
                        .withLocation(0, 0, 4464, 936));
        MessageHandler handler = newHandler(session, resources);
        session.markMapTemplateSent(0);

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        drainMessages(session);
        session.bindPlayer(session.player().withLocation(1, 0, 20, 1008));
        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        Message mapInfo = drainMessages(session).getFirst();

        var reader = mapInfo.reader();
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(4374, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(4464, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals("Bờ sông Pu", reader.readUtf());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void finishLoadRegistersPresenceAndMovementDoesNotAckMover() throws Exception {
        AuthService auth = new AuthService();
        MapService maps = new MapService(new PlayerPacketWriter());
        ServerServices services = new ServerServices(auth, ResourceService.unavailable(), maps);
        Session first = inGameSession(services, PlayerProfile.initial("user01", 1, "alpha1", 0));
        Session second = inGameSession(services, PlayerProfile.initial("user02", 2, "beta22", 0));
        MessageHandler firstHandler = newHandler(first, services, NetworkConfig.defaults());
        MessageHandler secondHandler = newHandler(second, services, NetworkConfig.defaults());

        firstHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        secondHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        assertEquals(1, first.queuedMessages());
        assertEquals(1, second.queuedMessages());
        drainMessages(first);
        drainMessages(second);

        secondHandler.onMessage(moveMessage(1260, 640));
        assertEquals(1, first.queuedMessages());
        assertEquals(0, second.queuedMessages());
        Message movement = drainMessages(first).get(0);
        var reader = movement.reader();
        assertEquals(MessageName.PLAYER_MOVE, movement.command());
        assertEquals(2, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerMoveIsAcceptedInGameAndUpdatesSessionPosition() {
        AuthService auth = new AuthService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1284, 620));
        handler.onMessage(moveMessage(1312, 648));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(1312, session.player().x());
        assertEquals(648, session.player().y());
        assertEquals(0, session.queuedMessages());
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
        MapService maps = new MapService(new PlayerPacketWriter());
        ServerServices services = new ServerServices(new AuthService(), ResourceService.unavailable(), maps);
        Session session = inGameSession(services, PlayerProfile.initial("user01", 7, "alpha1", 0));
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, maps.memberCount(0, 0));
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
    void playerMoveRejectsTruncatedPayload() {
        AuthService auth = new AuthService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        byte[] truncated = new MessageWriter()
                .writeShort(1260)
                .writeByte(1)
                .toByteArray();

        handler.onMessage(new Message(MessageName.PLAYER_MOVE, truncated));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveRejectsTrailingPayloadBytes() {
        AuthService auth = new AuthService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        byte[] trailing = new MessageWriter()
                .writeShort(1260)
                .writeShort(648)
                .writeByte(1)
                .toByteArray();

        handler.onMessage(new Message(MessageName.PLAYER_MOVE, trailing));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveRemainsRejectedBeforeInGame() {
        AuthService auth = new AuthService();
        Session session = newSession(auth);
        session.bindPlayer(PlayerProfile.initial("user01", 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1260, 648));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveWithoutBoundPlayerFailsClosed() {
        AuthService auth = new AuthService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);

        newHandler(session, auth).onMessage(moveMessage(1260, 648));

        assertEquals(SessionState.CLOSED, session.state());
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
    void serializesMovementEffectResourceInUnityFieldOrder() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
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
                    new ServerServices(new AuthService(), resources),
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
                assertEquals(0, reader.readByte());
                assertEquals(2, reader.readUnsignedShort());
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
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(new Message(
                MessageName.UPDATE_DATA, new byte[]{4}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void manifestAdvertisesLoadedMonsterVersionOne() throws Exception {
        assertEquals(1, readManifestMonsterVersion(ResourceService.fromFrameRoot(
                Path.of("resources", "json"))));
    }

    @Test
    void manifestAdvertisesUnavailableMonsterVersionMinusOne() throws Exception {
        assertEquals(-1, readManifestMonsterVersion(ResourceService.unavailable()));
    }

    @Test
    void doesNotSendEmptyEffectDatasetWhenEffectResourcesAreUnavailable() {
        Session session = newSession(new AuthService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{3}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
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

    private static Message moveMessage(int x, int y) {
        return new Message(
                MessageName.PLAYER_MOVE,
                new MessageWriter().writeShort(x).writeShort(y).toByteArray());
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

    private static Session inGameSessionWithPlayer(AuthService auth) {
        Session session = newSession(auth);
        session.bindPlayer(PlayerProfile.initial("user01", 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    private static Session inGameSession(ServerServices services, PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> drainMessages(Session session) throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        BlockingQueue<Message> queue = (BlockingQueue<Message>) field.get(session);
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
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

    private static int readManifestMonsterVersion(ResourceService resources) throws Exception {
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
}
