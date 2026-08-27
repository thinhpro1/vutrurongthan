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
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.RuntimeMonster;
import com.project.game.player.PlayerProfile;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapServiceTest {
    @Test
    void finishLoadExchangesPresenceOnlyWithExistingSameZoneMembers() throws Exception {
        MapService maps = mapsWithoutMonsters();
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
        MapService maps = mapsWithoutMonsters();
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
        MapService maps = mapsWithoutMonsters();
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
        MapService maps = mapsWithoutMonsters();
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
        MapService maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));

        maps.finishLoad(first);
        maps.finishLoad(first);

        assertEquals(1, maps.memberCount(0, 0));
        assertEquals(List.of(), drain(first));
    }

    @Test
    void closedMembersAreNotSentPackets() throws Exception {
        MapService maps = mapsWithoutMonsters();
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

    @Test
    void simultaneousSameZoneJoinsExchangeOnePresencePacketEach() throws Exception {
        MapService maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstJoin = Thread.ofVirtual().start(() -> joinAtBarrier(start, maps, first, failure));
        Thread secondJoin = Thread.ofVirtual().start(() -> joinAtBarrier(start, maps, second, failure));

        start.await();
        firstJoin.join();
        secondJoin.join();

        if (failure.get() != null) {
            throw new AssertionError("concurrent join failed", failure.get());
        }
        assertEquals(2, maps.memberCount(0, 0));
        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(firstMessages));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(secondMessages));
        var firstReader = firstMessages.get(0).reader();
        var secondReader = secondMessages.get(0).reader();
        assertEquals(2, firstReader.readInt());
        assertEquals(1, secondReader.readInt());
    }

    @Test
    void leaveLastThenRejoinUsesRetainedZoneAndRemainsDiscoverable() throws Exception {
        MapService maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.finishLoad(first);
        maps.leave(first);
        assertEquals(0, maps.memberCount(0, 0));

        maps.finishLoad(second);
        assertEquals(1, maps.memberCount(0, 0));
        maps.finishLoad(first);

        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(firstMessages));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(secondMessages));
        var firstReader = firstMessages.get(0).reader();
        var secondReader = secondMessages.get(0).reader();
        assertEquals(2, firstReader.readInt());
        assertEquals(1, secondReader.readInt());
        assertEquals(2, maps.memberCount(0, 0));
    }

    @Test
    void zoneOwnsOrderedMonsterSnapshots() {
        List<RuntimeMonster> runtimes =
                monsterFactory().createForMap(1);

        Zone zone = new Zone(1, 0, runtimes);

        List<MonsterSnapshot> snapshots = zone.monsterSnapshots();
        assertEquals(6, snapshots.size());
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5),
                snapshots.stream().map(MonsterSnapshot::id).toList());
        assertEquals(
                List.of(975, 1348, 1800, 2250, 2600, 2950),
                snapshots.stream().map(MonsterSnapshot::x).toList());
    }

    @Test
    void zoneRejectsDuplicateMonsterRuntimeIds() {
        List<RuntimeMonster> runtimes =
                monsterFactory().createForMap(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Zone(
                        1,
                        0,
                        List.of(runtimes.getFirst(), runtimes.getFirst())));
    }

    @Test
    void monsterSnapshotCreatesZoneWithoutJoiningPlayer() {
        MapService maps = mapsWithMonsters();

        List<MonsterSnapshot> monsters = maps.monsterSnapshots(1, 0);

        assertEquals(6, monsters.size());
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5),
                monsters.stream().map(MonsterSnapshot::id).toList());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void mapZeroZoneStartsWithoutRuntimeMonsters() {
        MapService maps = mapsWithMonsters();
        assertTrue(maps.monsterSnapshots(0, 0).isEmpty());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void finishLoadReusesZoneCreatedForMonsterSnapshot() throws Exception {
        MapService maps = mapsWithMonsters();
        List<MonsterSnapshot> before = maps.monsterSnapshots(1, 0);
        Session joining = session(player(1, 1, 0), maps);

        assertEquals(0, maps.memberCount(1, 0));
        maps.finishLoad(joining);

        assertEquals(1, maps.memberCount(1, 0));
        assertEquals(before, maps.monsterSnapshots(1, 0));
    }

    @Test
    void differentMap1ZonesStartWithEquivalentSeeds() {
        MapService maps = mapsWithMonsters();
        List<MonsterSnapshot> zone0 = maps.monsterSnapshots(1, 0);
        List<MonsterSnapshot> zone1 = maps.monsterSnapshots(1, 1);

        assertEquals(zone0, zone1);
        assertEquals(6, zone0.size());
        assertEquals(6, zone1.size());
    }

    @Test
    void concurrentMonsterSnapshotsRemainStableForSameZone() throws Exception {
        MapService maps = mapsWithMonsters();
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<List<MonsterSnapshot>> first = new AtomicReference<>();
        AtomicReference<List<MonsterSnapshot>> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread one = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                first.set(maps.monsterSnapshots(1, 0));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });

        Thread two = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                second.set(maps.monsterSnapshots(1, 0));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });

        start.await();
        one.join();
        two.join();

        if (failure.get() != null) {
            throw new AssertionError(
                    "concurrent monster snapshot failed",
                    failure.get());
        }

        assertEquals(first.get(), second.get());
        assertEquals(6, first.get().size());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void disconnectCannotFinishWhileJoinPresenceEnqueueIsInProgress() throws Exception {
        MapService maps = mapsWithoutMonsters();
        Session leaving = session(player(1, 0, 0), maps);
        Session joining = session(player(2, 0, 0), maps);
        maps.finishLoad(leaving);
        drain(leaving);

        BlockingOfferQueue joiningQueue = new BlockingOfferQueue();
        replaceSendQueue(joining, joiningQueue);
        Thread join = Thread.ofVirtual().start(() -> maps.finishLoad(joining));
        assertTrue(joiningQueue.offerEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch disconnectFinished = new CountDownLatch(1);
        Thread disconnect = Thread.ofVirtual().start(() -> {
            leaving.close();
            disconnectFinished.countDown();
        });
        assertFalse(disconnectFinished.await(1, TimeUnit.SECONDS));

        joiningQueue.releaseOffer.countDown();
        join.join();
        disconnect.join();
        assertEquals(List.of(MessageName.ADD_PLAYER, MessageName.REMOVE_PLAYER),
                commands(drain(joining)));
    }

    private static void joinAtBarrier(CyclicBarrier start, MapService maps,
                                      Session session, AtomicReference<Throwable> failure) {
        try {
            start.await();
            maps.finishLoad(session);
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
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
        return session(player, ServerServices.defaults());
    }

    private static MonsterRuntimeFactory monsterFactory() {
        return new MonsterRuntimeFactory(
                ResourceService.fromFrameRoot(
                        Path.of("resources", "json")));
    }

    private static MapService mapsWithMonsters() {
        return new MapService(
                new PlayerPacketWriter(),
                monsterFactory());
    }

    private static MapService mapsWithoutMonsters() {
        ResourceService resources = ResourceService.unavailable();
        return new MapService(
                new PlayerPacketWriter(),
                new MonsterRuntimeFactory(resources));
    }

    private static Session session(PlayerProfile player, MapService maps) {
        return session(player, new ServerServices(new AuthService(), ResourceService.unavailable(), maps));
    }

    private static Session session(PlayerProfile player, ServerServices services) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                services, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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

    private static void replaceSendQueue(Session session, BlockingQueue<Message> replacement)
            throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        field.set(session, replacement);
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

    private static final class BlockingOfferQueue extends LinkedBlockingQueue<Message> {
        private final CountDownLatch offerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseOffer = new CountDownLatch(1);
        private final AtomicBoolean blockFirstOffer = new AtomicBoolean(true);

        @Override
        public boolean offer(Message message) {
            if (blockFirstOffer.compareAndSet(true, false)) {
                offerEntered.countDown();
                try {
                    releaseOffer.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("offer gate interrupted", exception);
                }
            }
            return super.offer(message);
        }
    }

}
