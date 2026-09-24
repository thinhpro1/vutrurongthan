package com.project.game.network;
import com.project.game.testsupport.TestPlayers;

import com.project.game.testsupport.TestServices;

import com.project.game.testsupport.GameplayServices;
import com.project.game.map.Zone;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AccountAuth;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import com.project.game.player.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

final class MessageHandlerTestSupport {

    static MessageHandler newHandler(Session session, AccountAuth auth) {
        return newHandler(session, TestServices.serverServices(auth, GameResources.unavailable()),
                ClientConfig.defaults());
    }

    static MessageHandler newHandler(Session session, GameResources resources) {
        return newHandler(session, TestServices.serverServices(TestServices.auth(), resources), ClientConfig.defaults());
    }

    static MessageHandler newHandler(Session session, SessionServices services, ClientConfig config) {
        return new MessageHandler(session, services, config);
    }

    static Session newSession(AccountAuth auth) {
        return newSession(auth, 1024);
    }

    static Session newSession(AccountAuth auth, int maxPacketSize) {
        return newSession(auth, maxPacketSize, new SessionManager(), "127.0.0.1");
    }

    static Session newSession(AccountAuth auth, int maxPacketSize,
                                      SessionManager manager, String remoteAddress) {
        return new Session(manager.nextId(), new TestTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), remoteAddress), manager,
                new LegacyPacketCodec(maxPacketSize), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(auth, GameResources.unavailable()), ClientConfig.defaults());
    }

    static Session inGameSessionWithPlayer(AccountAuth auth) {
        Session session = newSession(auth);
        session.bindPlayer(TestPlayers.initial(1L, 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    static Session inGameSession(SessionServices services, Player player) {
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
        Zone zone = maps.findZone(mapId, zoneId);
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
