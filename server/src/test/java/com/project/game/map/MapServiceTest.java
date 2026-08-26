package com.project.game.map;

import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapServiceTest {
    @Test
    void finishLoadExchangesPresenceOnlyWithExistingSameZoneMembers() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.finishLoad(first);
        assertEquals(List.of(), drain(first));

        maps.finishLoad(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(second)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(first)));
        assertEquals(2, maps.memberCount(0, 0));
    }

    @Test
    void differentZonesDoNotExchangePresence() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 1));

        maps.finishLoad(first);
        maps.finishLoad(second);

        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(second));
        assertEquals(1, maps.memberCount(0, 0));
        assertEquals(1, maps.memberCount(0, 1));
    }

    @Test
    void movementIsSentToOtherMembersWithoutMoverAck() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.finishLoad(first);
        maps.finishLoad(second);
        drain(first);
        drain(second);

        second.bindPlayer(second.player().withPosition(1260, 640));
        maps.playerMoved(second);

        List<Message> firstMessages = drain(first);
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(firstMessages));
        var reader = firstMessages.get(0).reader();
        assertEquals(2, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
        assertEquals(List.of(), drain(second));
    }

    @Test
    void leaveNotifiesOtherMembersOnce() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.finishLoad(first);
        maps.finishLoad(second);
        drain(first);
        drain(second);

        maps.leave(second);
        List<Message> removed = drain(first);
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(removed));
        var reader = removed.get(0).reader();
        assertEquals(2, reader.readInt());
        assertEquals(0, reader.remaining());
        assertEquals(1, maps.memberCount(0, 0));

        maps.leave(second);
        assertEquals(List.of(), drain(first));
    }

    @Test
    void repeatedFinishLoadDoesNotDuplicateMembershipOrPresence() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));

        maps.finishLoad(first);
        maps.finishLoad(first);

        assertEquals(1, maps.memberCount(0, 0));
        assertEquals(List.of(), drain(first));
    }

    @Test
    void closedMembersAreNotSentPackets() throws Exception {
        MapService maps = new MapService(new PlayerPacketWriter());
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.finishLoad(first);
        maps.finishLoad(second);
        drain(first);
        drain(second);
        second.close();

        first.bindPlayer(first.player().withPosition(1260, 640));
        maps.playerMoved(first);
        assertEquals(List.of(), drain(first));
    }

    private static PlayerProfile player(int id, int mapId, int zoneId) {
        PlayerProfile base = PlayerProfile.initial("user" + id, id, "player" + id, 0);
        return new PlayerProfile(base.accountName(), base.id(), base.name(), base.gender(), base.power(),
                base.potential(), base.level(), base.pointSkill(), base.head(), base.body(), base.mount(), base.bag(),
                base.medal(), base.aura(), base.baseDamage(), base.baseHp(), base.baseMp(), base.baseConstitution(),
                base.potentialUpDamage(), base.potentialUpHp(), base.potentialUpMp(), base.potentialUpConstitution(),
                base.maxHp(), base.maxMp(), base.hp(), base.mp(), base.speed(), base.pointPk(), base.pointActivity(),
                base.countBarrack(), base.dodge(), base.critical(), base.reduceDamage(), base.bloodsucking(),
                base.manaSucking(), base.strikeBack(), base.damage(), base.coin(), base.coinLock(), base.diamond(),
                base.ruby(), base.spaceship(), mapId, zoneId, base.x(), base.y());
    }

    private static Session session(PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                ServerServices.defaults(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> drain(Session session) throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        BlockingQueue<Message> queue = (BlockingQueue<Message>) field.get(session);
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }

    private static List<Integer> commands(List<Message> messages) {
        return messages.stream().map(Message::command).toList();
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return output;
        }

        @Override
        public String remoteAddress() {
            return "map-test";
        }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}
