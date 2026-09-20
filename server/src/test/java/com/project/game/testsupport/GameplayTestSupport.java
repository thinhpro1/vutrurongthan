package com.project.game.testsupport;

import com.project.game.map.Zone;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.RuntimeMonster;
import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.resource.GameResources;
import com.project.game.service.ServerServices;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class GameplayTestSupport {
    private GameplayTestSupport() {}
public static void joinAtBarrier(CyclicBarrier start, GameplayServices maps,
                                      Session session, AtomicReference<Throwable> failure) {
        try {
            start.await();
            maps.mapService().finishLoad(session);
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    public static PlayerProfile player(int id, int mapId, int zoneId) {
        return PlayerProfile.initial((long) id, id, "player" + id, 0)
                .withLocation(mapId, zoneId, 1250, 648)
                .withHp(100);
    }

    public static Session session(PlayerProfile player) {
        return session(player, TestServices.serverServices());
    }

    public static void attackAtBarrier(CyclicBarrier start, GameplayServices maps,
                                        Session session, AtomicBoolean result,
                                        AtomicReference<Throwable> failure) {
        try {
            start.await();
            result.set(maps.combatService().attackMonster(session, 0, 10));
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    public static MonsterRuntimeFactory monsterFactory() {
        return new MonsterRuntimeFactory(
                GameResources.fromFrameRoot(
                        Path.of("resources", "json")));
    }

    public static GameplayServices mapsWithMonsters() {
        return new GameplayServices(GameResources.fromFrameRoot(Path.of("resources", "json")));
    }

    public static GameplayServices mapsWithMonsters(Clock clock) {
        return new GameplayServices(GameResources.fromFrameRoot(Path.of("resources", "json")), clock);
    }

    public static GameplayServices mapsWithMonsters(Clock clock, java.util.random.RandomGenerator random) {
        return new GameplayServices(GameResources.fromFrameRoot(Path.of("resources", "json")), clock, random);
    }

    public static GameplayServices mapsWithoutMonsters() {
        GameResources resources = GameResources.unavailable();
        return new GameplayServices(resources);
    }

    public static int zoneRegistrySize(GameplayServices maps) {
        try {
            return maps.zones().snapshot().size();
        } catch (RuntimeException exception) {
            throw new AssertionError("unable to inspect zone registry", exception);
        }
    }

    public static Session session(PlayerProfile player, GameplayServices maps) {
        return session(player, TestServices.serverServices(TestServices.authService(),
                GameResources.unavailable(), maps));
    }

    public static Session session(PlayerProfile player, ServerServices services) {
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

    public static void setIntField(RuntimeMonster monster, String fieldName, int value)
            throws Exception {
        Field field = RuntimeMonster.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(monster, value);
    }

    @SuppressWarnings("unchecked")
    public static List<Message> drain(Session session) throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        BlockingQueue<Message> queue = (BlockingQueue<Message>) field.get(session);
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }

    public static List<Integer> commands(List<Message> messages) {
        return messages.stream()
                .map(Message::command)
                .toList();
    }

    public static List<Message> withoutMonsterMoves(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.command() != MessageName.MONSTER_MOVE)
                .toList();
    }

    public static void assertMonsterMove(Message message, int expectedId,
                                          int expectedX, int expectedY, int expectedDir)
            throws IOException {
        assertEquals(MessageName.MONSTER_MOVE, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedX, reader.readShort());
        assertEquals(expectedY, reader.readShort());
        assertEquals(expectedDir, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    public static int monsterMoveId(Message message) {
        try {
            return message.reader().readInt();
        } catch (IOException exception) {
            throw new AssertionError("invalid monster movement packet", exception);
        }
    }

    public static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.BLOCKED, thread.getState(),
                "movement did not block on zone monitor");
    }

    public static void replaceSendQueue(Session session, BlockingQueue<Message> replacement)
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

    public static final class BlockingOfferQueue extends LinkedBlockingQueue<Message> {
        public final CountDownLatch offerEntered = new CountDownLatch(1);
        public final CountDownLatch releaseOffer = new CountDownLatch(1);
        public final AtomicBoolean blockFirstOffer = new AtomicBoolean(true);

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

    public static final class BlockingRandom implements RandomGenerator {
        public final CountDownLatch entered = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);

        @Override
        public int nextInt(int bound) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("random selection was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return 0;
        }

        @Override
        public long nextLong() {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    public static Zone zoneFor(GameplayServices maps, int mapId, int zoneId) {
        Zone zone = maps.zones().find(mapId, zoneId);
        if (zone == null) {
            throw new AssertionError("zone not found: " + mapId + "/" + zoneId);
        }
        return zone;
    }

    @SuppressWarnings("unchecked")
    public static List<RuntimeMonster> runtimeMonsters(GameplayServices maps, int mapId, int zoneId) {
        try {
            Zone zone = maps.zones().find(mapId, zoneId);
            if (zone == null) throw new AssertionError("zone not found: " + mapId + "/" + zoneId);
            Field monstersField = Zone.class.getDeclaredField("monsters");
            monstersField.setAccessible(true);
            return List.copyOf(((java.util.Map<Integer, RuntimeMonster>) monstersField.get(zone)).values());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("unable to inspect zone monsters", exception);
        }
    }
}
