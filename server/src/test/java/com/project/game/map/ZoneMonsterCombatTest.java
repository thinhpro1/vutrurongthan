package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.Monster.Snapshot;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageReader;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    void ownsOrderedMonsterSnapshotsAndDamageInvariant() throws Exception {
        Zone zone = map1Zone();
        Session attacker = session(playerAt(7, 975, 936));
        assertTrue(zone.enter(attacker));
        drain(attacker);

        assertTrue(zone.attackMonster(attacker, 101, NOW));

        assertFalse(zone.canTargetMonster(attacker, 99));
        assertFalse(zone.attackMonster(attacker, 99, NOW));
        assertEquals(List.of(101, 102, 103, 104, 105, 106),
                zone.monsterSnapshots().stream().map(Snapshot::id).toList());
        assertEquals(290L, zone.monsterSnapshots().getFirst().hp());
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        MessageReader injury = messages.getFirst().reader();
        assertEquals(101, injury.readInt());
        assertEquals(10L, injury.readLong());
        assertEquals(290L, injury.readLong());
        assertFalse(injury.readBoolean());
        assertEquals(0, injury.remaining());
    }

    @Test
    void semanticCombatApiOwnsTargetingAndDamageOrdering() throws Exception {
        Zone zone = map1Zone();
        Session attacker = session(playerAt(7, 975, 936));

        assertTrue(zone.enter(attacker));
        drain(attacker);

        assertTrue(zone.canTargetMonster(attacker, 101));
        assertTrue(zone.attackMonster(attacker, 101, NOW));
        assertEquals(290L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
    }

    @Test
    void damageCapturesCurrentMemberCountForRespawnDeadline() throws Exception {
        Zone zone = map1Zone();
        Session player = session(playerAt(1, 975, 936, 500));
        assertTrue(zone.enter(player));
        drain(player);

        assertTrue(zone.attackMonster(player, 101, NOW));
        assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
        drain(player);
        zone.updateMonsters(NOW + 9_000, new Random(1L));
        assertTrue(commands(drain(player)).stream()
                .noneMatch(command -> command == MessageName.MONSTER_RESPAWN));

        zone.updateMonsters(NOW + 9_001, new Random(1L));
        assertTrue(commands(drain(player)).contains(MessageName.MONSTER_RESPAWN));
        assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(0, zone.monsterSnapshots().getFirst().status());
    }

    @Test
    void zeroDamagePrepareAndImpactKeepExistingSemantics() throws Exception {
        Zone zone = map1Zone();
        Session attacker = session(playerAt(7, 975, 936, 0));
        assertTrue(zone.enter(attacker));
        drain(attacker);

        assertTrue(zone.canTargetMonster(attacker, 101));
        assertFalse(zone.attackMonster(attacker, 101, NOW));

        assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(1L, attacker.player().potential());
        assertTrue(drain(attacker).isEmpty());
    }

    @Test
    void combatRejectsStaleBacklinkAndDifferentSessionWithSamePlayerId() throws Exception {
        Zone zone = map1Zone();
        Session member = session(playerAt(7, 975, 936));
        assertTrue(zone.enter(member));
        drain(member);
        Session staleBacklink = session(playerAt(8, 975, 936));
        Session samePlayerId = session(playerAt(7, 975, 936));
        staleBacklink.bindZone(zone);
        samePlayerId.bindZone(zone);

        for (Session nonMember : List.of(staleBacklink, samePlayerId)) {
            assertFalse(zone.hasPlayer(nonMember));
            assertFalse(zone.canTargetMonster(nonMember, 101));
            assertFalse(zone.attackMonster(nonMember, 101, NOW));
            assertEquals(1L, nonMember.player().potential());
            assertTrue(drain(nonMember).isEmpty());
        }

        assertTrue(zone.hasPlayer(member));
        assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(1L, member.player().potential());
        assertTrue(drain(member).isEmpty());
    }

    @Test
    void queuedAttackRevalidatesMembershipAfterDetach() throws Exception {
        Zone zone = map1Zone();
        Session attacker = session(playerAt(7, 975, 936));
        assertTrue(zone.enter(attacker));
        drain(attacker);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(zone, () -> {
            writerStarted.countDown();
            awaitRelease(releaseWriter);
        }));
        assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
        AtomicReference<PlayerSaveData> detached = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean attacked = new AtomicBoolean();
        Thread detach = Thread.ofVirtual().start(() -> {
            try {
                detached.set(zone.leave(attacker));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
        Thread hit = null;
        try {
            awaitWaiting(detach);
            hit = Thread.ofVirtual().start(() -> {
                try {
                    attacked.set(zone.attackMonster(attacker, 101, NOW));
                } catch (Throwable exception) {
                    failure.compareAndSet(null, exception);
                }
            });
            awaitWaiting(hit);
            releaseWriter.countDown();
            detach.join(5_000);
            hit.join(5_000);

            assertFalse(detach.isAlive());
            assertFalse(hit.isAlive());
            assertNull(failure.get());
            assertNotNull(detached.get());
            assertFalse(attacked.get());
            assertFalse(zone.hasPlayer(attacker));
            assertNull(attacker.zone());
            assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
            assertEquals(1L, attacker.player().potential());
            assertTrue(drain(attacker).isEmpty());
        } finally {
            releaseWriter.countDown();
            detach.join(5_000);
            if (hit != null) {
                hit.join(5_000);
            }
        }
    }

    @Test
    void writerOrderDecidesFinisherInBothOrders() throws Exception {
        assertFinisherOrder(true);
        assertFinisherOrder(false);
    }

    @Test
    void combatRejectsFullOrStoppedWriterWithoutDelayedMutation() throws Exception {
        Zone zone = new Zone(1, 0, Integer.MAX_VALUE, monsters(), 1,
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
        Session attacker = session(playerAt(7, 975, 936));
        assertTrue(zone.enter(attacker));
        drain(attacker);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        CountDownLatch queuedAction = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(zone, () -> {
            writerStarted.countDown();
            awaitRelease(releaseWriter);
        }));
        assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
        try {
            assertTrue(ZoneTestHooks.submit(zone, queuedAction::countDown));
            assertFalse(zone.canTargetMonster(attacker, 101));
            assertFalse(zone.attackMonster(attacker, 101, NOW));

            releaseWriter.countDown();
            assertTrue(queuedAction.await(5, TimeUnit.SECONDS));
            ZoneTestHooks.call(zone, () -> null);
            assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
            assertEquals(1L, attacker.player().potential());
            assertTrue(drain(attacker).isEmpty());

            zone.stopRuntime();
            assertFalse(zone.canTargetMonster(attacker, 101));
            assertFalse(zone.attackMonster(attacker, 101, NOW));
            assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
            assertEquals(1L, attacker.player().potential());
            assertTrue(drain(attacker).isEmpty());
        } finally {
            releaseWriter.countDown();
        }
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
        assertTrue(zone.enter(target));
        drain(target);
        assertTrue(zone.attackMonster(target, 101, NOW));
        drain(target);
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
        assertTrue(zone.enter(victim));
        drain(victim);
        ZoneTestHooks.call(zone, () -> {
            assertNotNull(monsters.get(0).injure(7, 10, NOW, zone.size()));
            assertNotNull(monsters.get(1).injure(7, 10, NOW, zone.size()));
            return null;
        });

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
        assertTrue(zone.enter(first));

        assertTrue(zone.hasPlayer(first));
        assertFalse(zone.hasPlayer(samePlayerId));
    }

    private static Zone map1Zone() {
        return zone(1, 0, Integer.MAX_VALUE, monsters());
    }

    private static List<Monster> monsters() {
        MonsterManager manager = new MonsterManager(GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
        return manager.createForMap(1);
    }

    private static Zone zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters) {
        return new Zone(mapId, zoneId, maxPlayer, monsters,
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
    }

    private static Player playerAt(int id, int x, int y) {
        return TestPlayers.at(TestPlayers.initial((long) id, id, "player" + id, 1),
                1, 0, x, y);
    }

    private static Player playerAt(int id, int x, int y, int damage) {
        Player player = playerAt(id, x, y);
        Player.CurrentStats stats = player.currentStats();
        Player.CurrentStats current = new Player.CurrentStats(
                stats.maxHp(), stats.maxMp(), damage, stats.armor(), stats.critical(),
                stats.dodge(), stats.constitution(), stats.speed());
        return new Player(player.id(), player.accountId(), player.name(), player.gender(),
                player.power(), player.potential(), player.level(), player.exp(),
                player.baseStats(), current, player.hp(), player.mp(), player.appearance(),
                player.coin(), player.coinLock(), player.diamond(), player.ruby(),
                player.mapId(), player.zoneId(), player.x(), player.y());
    }

    private static void assertFinisherOrder(boolean aFirst) throws Exception {
        List<Monster> monsters = monsters();
        assertNotNull(monsters.getFirst().injure(99, 200, NOW, 0));
        Zone zone = zone(1, 0, Integer.MAX_VALUE, monsters);
        Session playerA = session(playerAt(7, 975, 936, 60));
        Session playerB = session(playerAt(8, 975, 936, 70));
        assertTrue(zone.enter(playerA));
        assertTrue(zone.enter(playerB));
        drain(playerA);
        drain(playerB);
        Session first = aFirst ? playerA : playerB;
        Session second = aFirst ? playerB : playerA;
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(zone, () -> {
            writerStarted.countDown();
            awaitRelease(releaseWriter);
        }));
        assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
        AtomicBoolean firstAttacked = new AtomicBoolean();
        AtomicBoolean secondAttacked = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstHit = attack(zone, first, firstAttacked, failure);
        Thread secondHit = null;
        try {
            awaitWaiting(firstHit);
            secondHit = attack(zone, second, secondAttacked, failure);
            awaitWaiting(secondHit);
            releaseWriter.countDown();
            firstHit.join(5_000);
            secondHit.join(5_000);

            assertFalse(firstHit.isAlive());
            assertFalse(secondHit.isAlive());
            assertNull(failure.get());
            assertTrue(firstAttacked.get());
            assertTrue(secondAttacked.get());
            assertEquals(aFirst ? 1L : 11L, playerA.player().potential());
            assertEquals(aFirst ? 11L : 1L, playerB.player().potential());
            assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
            assertEquals(List.of(MessageName.MONSTER_INJURE, MessageName.MONSTER_START_DIE),
                    commands(drain(first)));
            assertEquals(List.of(MessageName.MONSTER_INJURE, MessageName.MONSTER_START_DIE,
                            MessageName.PLAYER_INFO),
                    commands(drain(second)));

            assertFalse(zone.attackMonster(playerA, 101, NOW + 1));
            assertFalse(zone.attackMonster(playerB, 101, NOW + 1));
            assertEquals(1L, first.player().potential());
            assertEquals(11L, second.player().potential());
            assertTrue(drain(playerA).isEmpty());
            assertTrue(drain(playerB).isEmpty());
        } finally {
            releaseWriter.countDown();
            firstHit.join(5_000);
            if (secondHit != null) {
                secondHit.join(5_000);
            }
        }
    }

    private static Thread attack(Zone zone, Session attacker, AtomicBoolean attacked,
                                 AtomicReference<Throwable> failure) {
        return Thread.ofVirtual().start(() -> {
            try {
                attacked.set(zone.attackMonster(attacker, 101, NOW));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
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
                "caller did not wait for the Zone writer");
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
