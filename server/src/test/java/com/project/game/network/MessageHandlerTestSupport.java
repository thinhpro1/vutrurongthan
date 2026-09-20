package com.project.game.network;
import com.project.game.testsupport.TestPlayerProfiles;

import com.project.game.testsupport.TestServices;

import com.project.game.testsupport.GameplayServices;
import com.project.game.map.Zone;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AuthService;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import com.project.game.player.PlayerProfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

final class MessageHandlerTestSupport {

    static MessageHandler newHandler(Session session, AuthService authService) {
        return newHandler(session, TestServices.serverServices(authService, GameResources.unavailable()),
                ClientConfig.defaults());
    }

    static MessageHandler newHandler(Session session, GameResources resources) {
        return newHandler(session, TestServices.serverServices(TestServices.authService(), resources), ClientConfig.defaults());
    }

    static MessageHandler newHandler(Session session, SessionServices services, ClientConfig config) {
        return new MessageHandler(session, services, config);
    }

    static Session newSession(AuthService authService) {
        return newSession(authService, 1024);
    }

    static Session newSession(AuthService authService, int maxPacketSize) {
        return newSession(authService, maxPacketSize, new SessionManager(), "127.0.0.1");
    }

    static Session newSession(AuthService authService, int maxPacketSize,
                                      SessionManager manager, String remoteAddress) {
        return new Session(manager.nextId(), new TestTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), remoteAddress), manager,
                new LegacyPacketCodec(maxPacketSize), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(authService, GameResources.unavailable()), ClientConfig.defaults());
    }

    static Session inGameSessionWithPlayer(AuthService auth) {
        Session session = newSession(auth);
        session.bindPlayer(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    static Session inGameSession(SessionServices services, PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, ClientConfig.defaults());
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    static List<Message> drainMessages(Session session) throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        BlockingQueue<Message> queue = (BlockingQueue<Message>) field.get(session);
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }

    static Zone zoneFor(GameplayServices maps, int mapId, int zoneId) throws Exception {
        Zone zone = maps.zones().find(mapId, zoneId);
        if (zone == null) {
            throw new AssertionError("zone not found map=" + mapId + " zone=" + zoneId);
        }
        return zone;
    }

    static Message loginMessage(String username, String password) throws IOException {
        return new Message(MessageName.LOGIN, new MessageWriter()
                .writeUtf("0.9.5")
                .writeUtf(username)
                .writeUtf(password)
                .writeByte(1)
                .toByteArray());
    }

    static Message moveMessage(int x, int y) {
        return new Message(
                MessageName.PLAYER_MOVE,
                new MessageWriter().writeShort(x).writeShort(y).toByteArray());
    }
}
