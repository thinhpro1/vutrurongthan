package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.MessageName;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.Player;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;
import com.project.game.testsupport.TestPlayers;
import com.project.game.testsupport.TestServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneMonsterCombatTest {
    private static final long NOW = 1_000_000L;

    @Test
    void ownsOrderedMonsterSnapshotsAndThinDamageBridge() {
        Zone zone = map1Zone();

        Monster.Damage damage = zone.damageMonster(101, 7, 10, NOW);

        assertEquals(new Monster.Damage(101, 10, 290, false, 0L), damage);
        assertNull(zone.damageMonster(99, 7, 10, NOW));
        assertEquals(List.of(101, 102, 103, 104, 105, 106),
                zone.monsterSnapshots().stream().map(MonsterSnapshot::id).toList());
        assertEquals(290L, zone.monsterSnapshots().getFirst().hp());
    }

    @Test
    void damageCapturesCurrentMemberCountForRespawnDeadline() throws Exception {
        Zone zone = map1Zone();
        Session player = session(playerAt(1, 975, 936));
        zone.addPlayer(player);
        drain(player);

        assertTrue(zone.damageMonster(101, 1, 500, NOW).killed());
        zone.updateMonsters(NOW + 9_000, new Random(1L));
        assertTrue(commands(drain(player)).stream()
                .noneMatch(command -> command == MessageName.MONSTER_RESPAWN));

        zone.updateMonsters(NOW + 9_001, new Random(1L));
        assertTrue(commands(drain(player)).contains(MessageName.MONSTER_RESPAWN));
        assertTrue(zone.hasLiveMonster(101));
    }

    @Test
    void monsterLifecycleWaitsForAdmissionWhenZoneInputQueueIsFull() throws Exception {
        List<Monster> monsters = monsters();
        Zone zone = new Zone(1, 0, Integer.MAX_VALUE, monsters, 1,
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch queuedAction = new CountDownLatch(1);
        assertTrue(zone.submit(queuedAction::countDown));

        CountDownLatch lifecycleStarted = new CountDownLatch(1);
        CountDownLatch lifecycleFinished = new CountDownLatch(1);
        Thread lifecycle = Thread.ofVirtual().start(() -> {
            lifecycleStarted.countDown();
            try {
                zone.updateMonsters(NOW, new Random(1L));
            } finally {
                lifecycleFinished.countDown();
            }
        });

        try {
            assertTrue(lifecycleStarted.await(5, TimeUnit.SECONDS));
            awaitWaiting(lifecycle);
            assertFalse(queuedAction.await(100, TimeUnit.MILLISECONDS));

            releasePrior.countDown();
            assertTrue(lifecycleFinished.await(5, TimeUnit.SECONDS));
            lifecycle.join();

            assertTrue(queuedAction.getCount() == 0);
            assertEquals(979, zone.monsterSnapshots().getFirst().x());
        } finally {
            releasePrior.countDown();
            lifecycle.join(5_000);
        }
    }

    @Test
    void lifecycleUsesOnlyExactLiveZoneMembersAsMonsterTargets() throws Exception {
        Zone zone = map1Zone();
        Session target = session(playerAt(7, 975, 936));
        zone.addPlayer(target);
        drain(target);
        assertNotNull(zone.damageMonster(101, 7, 10, NOW));
        target.transition(SessionState.CONNECTED, SessionState.CLOSED);

        zone.updateMonsters(NOW + 1, new Random(1L));

        assertFalse(commands(drain(target)).contains(MessageName.MONSTER_ATTACK));
    }

    @Test
    void lethalMonsterAttackClearsVictimFromEveryMonster() throws Exception {
        List<Monster> monsters = monsters();
        Zone zone = zone(1, 0, Integer.MAX_VALUE, monsters);
        Player player = playerAt(7, 975, 936);
        player.injure(190);
        Session victim = session(player);
        zone.addPlayer(victim);
        drain(victim);
        assertNotNull(zone.damageMonster(101, 7, 10, NOW));
        assertNotNull(zone.damageMonster(102, 7, 10, NOW));

        zone.updateMonsters(NOW + 1, new Random(1L));

        assertEquals(0L, player.hp());
        assertTrue(monsters.stream().noneMatch(monster -> monster.hasEnemy(player.id())));
        assertEquals(List.of(MessageName.MONSTER_ATTACK, MessageName.ME_DIE),
                commands(drain(victim)).stream()
                        .filter(command -> command != MessageName.MONSTER_MOVE)
                        .toList());
    }

    @Test
    void zoneRejectsDuplicateRuntimeIdsAndRequiresExactSessionIdentity() {
        List<Monster> monsters = monsters();
        assertThrows(IllegalArgumentException.class,
                () -> zone(1, 0, Integer.MAX_VALUE,
                        List.of(monsters.getFirst(), monsters.getFirst())));

        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Session first = session(playerAt(7, 975, 936));
        Session samePlayerId = session(playerAt(7, 975, 936));
        zone.addPlayer(first);

        assertTrue(zone.hasPlayer(first));
        assertFalse(zone.hasPlayer(samePlayerId));
    }

    private static Zone map1Zone() {
        return zone(1, 0, Integer.MAX_VALUE, monsters());
    }

    private static List<Monster> monsters() {
        MonsterManager factory = new MonsterManager(GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
        return factory.createForMap(1);
    }

    private static Zone zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters) {
        return new Zone(mapId, zoneId, maxPlayer, monsters,
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
    }

    private static Player playerAt(int id, int x, int y) {
        return TestPlayers.at(TestPlayers.initial((long) id, id, "player" + id, 1),
                1, 0, x, y);
    }

    private static Session session(Player player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                TestServices.serverServices(), ClientConfig.defaults());
        session.bindPlayer(player);
        return session;
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("prior Zone action was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TERMINATED
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.WAITING, thread.getState(),
                "monster lifecycle did not wait for Zone admission");
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() { return input; }

        @Override
        public OutputStream output() { return output; }

        @Override
        public String remoteAddress() { return "zone-monster-test"; }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}
