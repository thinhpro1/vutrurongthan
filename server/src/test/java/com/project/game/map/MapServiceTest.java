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
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.RuntimeMonster;
import com.project.game.player.PlayerProfile;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import com.project.game.test.MutableClock;
import org.junit.jupiter.api.Test;

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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void combatCannotCreateZone() {
        MapService maps = mapsWithMonsters();
        Session session = session(player(1, 1, 0), maps);

        assertEquals(0, zoneRegistrySize(maps));
        assertFalse(maps.canTargetMonster(session, 0));
        assertFalse(maps.attackMonster(session, 0, 10));
        assertEquals(0, zoneRegistrySize(maps));
    }

    @Test
    void mapInfoCreatedButPreFinishSessionCannotCombat() {
        MapService maps = mapsWithMonsters();
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        Session session = session(player(1, 1, 0), maps);

        assertEquals(0, maps.memberCount(1, 0));
        assertFalse(maps.canTargetMonster(session, 0));
        assertFalse(maps.attackMonster(session, 0, 10));
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void postFinishAttackSendsAuthoritativeInjureToAttacker() throws Exception {
        MapService maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.canTargetMonster(attacker, 0));
        assertTrue(maps.attackMonster(attacker, 0, 10));
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(0, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void sameZoneReceivesIdenticalCombatBroadcast() throws Exception {
        MapService maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);
        maps.finishLoad(attacker);
        maps.finishLoad(peer);
        drain(attacker);
        drain(peer);

        assertTrue(maps.attackMonster(attacker, 0, 10));
        List<Message> attackerMessages = drain(attacker);
        List<Message> peerMessages = drain(peer);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(peerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), peerMessages.getFirst().payload());
    }

    @Test
    void crossZoneDoesNotReceiveCombatBroadcast() throws Exception {
        MapService maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        Session otherZone = session(player(2, 1, 1), maps);
        maps.finishLoad(attacker);
        maps.finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.attackMonster(attacker, 0, 10));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
        assertEquals(List.of(), drain(otherZone));
        assertEquals(300L, maps.monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void concurrentLethalAttacksProduceOneDeathBroadcast() throws Exception {
        MapService maps = mapsWithMonsters();
        Session first = session(player(1, 1, 0), maps);
        Session second = session(player(2, 1, 0), maps);
        maps.finishLoad(first);
        maps.finishLoad(second);
        drain(first);
        drain(second);
        for (int i = 0; i < 29; i++) {
            assertTrue(maps.attackMonster(first, 0, 10));
            drain(first);
            drain(second);
        }
        drain(first);
        drain(second);

        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean firstResult = new AtomicBoolean();
        AtomicBoolean secondResult = new AtomicBoolean();
        Thread firstAttack = Thread.ofVirtual().start(() -> attackAtBarrier(
                start, maps, first, firstResult, failure));
        Thread secondAttack = Thread.ofVirtual().start(() -> attackAtBarrier(
                start, maps, second, secondResult, failure));
        start.await();
        firstAttack.join();
        secondAttack.join();

        if (failure.get() != null) {
            throw new AssertionError("concurrent combat failed", failure.get());
        }
        assertTrue(firstResult.get() ^ secondResult.get());
        assertEquals(0L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());
        assertEquals(1, commands(drain(first)).stream()
                .filter(command -> command == MessageName.MONSTER_START_DIE).count());
        assertEquals(1, commands(drain(second)).stream()
                .filter(command -> command == MessageName.MONSTER_START_DIE).count());
    }

    @Test
    void respawnTickDoesNotCreateZones() {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);

        assertEquals(0, zoneRegistrySize(maps));
        maps.tickMonsterLifecycle();
        assertEquals(0, zoneRegistrySize(maps));
    }

    @Test
    void onePlayerMonsterRespawnsOnlyAfterNineSecondDeadline() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.attackMonster(attacker, 0, 500));
        assertEquals(List.of(MessageName.MONSTER_START_DIE), commands(drain(attacker)));

        clock.advanceMillis(9_000L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
        assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());

        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(300L, reader.readLong());
        assertEquals(0, reader.remaining());
        MonsterSnapshot snapshot = maps.monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, snapshot.hp());
        assertEquals(0, snapshot.status());
    }

    @Test
    void sameZoneMembersReceiveOneRespawnBroadcastEach() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.finishLoad(attacker);
        maps.finishLoad(peer);
        drain(attacker);
        drain(peer);
        assertTrue(maps.attackMonster(attacker, 0, 500));
        drain(attacker);
        drain(peer);

        clock.advanceMillis(8_001L);
        maps.tickMonsterLifecycle();
        List<Message> attackerMessages = drain(attacker);
        List<Message> peerMessages = drain(peer);
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(peerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), peerMessages.getFirst().payload());

        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
        assertEquals(List.of(), drain(peer));
    }

    @Test
    void crossZoneDoesNotReceiveRespawnBroadcast() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session other = session(player(2, 1, 1), maps);

        maps.finishLoad(attacker);
        maps.finishLoad(other);
        drain(attacker);
        drain(other);
        assertTrue(maps.attackMonster(attacker, 0, 500));
        drain(attacker);

        clock.advanceMillis(9_001L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(drain(attacker)));
        assertEquals(List.of(), drain(other));
        assertEquals(300L, maps.monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void standingNearMonsterDoesNotAutoAggro() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock, new Random(12345L));
        Session player = session(player(1, 1, 0), maps);
        maps.finishLoad(player);
        drain(player);

        clock.advanceMillis(5_000L);
        maps.tickMonsterLifecycle();

        assertEquals(List.of(), drain(player));
        assertEquals(100L, player.player().hp());
    }

    @Test
    void retaliationBroadcastsToSameZoneAndMutatesOnlyTargetHp() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.finishLoad(attacker);
        maps.finishLoad(observer);
        drain(attacker);
        drain(observer);

        assertTrue(maps.attackMonster(attacker, 0, 10));
        drain(attacker);
        drain(observer);
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();

        List<Message> attackerMessages = drain(attacker);
        List<Message> observerMessages = drain(observer);
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(observerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), observerMessages.getFirst().payload());
        assertEquals(90L, attacker.player().hp());
        assertEquals(100L, observer.player().hp());
    }

    @Test
    void retaliationDoesNotCrossZones() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        Session otherZone = session(player(2, 1, 1), maps);
        maps.finishLoad(attacker);
        maps.finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.attackMonster(attacker, 0, 10));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();

        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(drain(attacker)));
        assertEquals(List.of(), drain(otherZone));
        assertEquals(100L, otherZone.player().hp());
    }

    @Test
    void retaliationCooldownIsStrictAndRangeUsesCurrentPosition() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        maps.finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.attackMonster(attacker, 0, 10));
        drain(attacker);

        attacker.bindPlayer(attacker.player().withPosition(975 + 901, 936));
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));

        attacker.bindPlayer(attacker.player().withPosition(975, 936));
        clock.advanceMillis(1_600L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(drain(attacker)));
        assertEquals(90L, attacker.player().hp());
    }

    @Test
    void retaliationStopsAtTenHpAndRespawnRequiresNewHit() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        attacker.bindPlayer(attacker.player().withHp(20));
        maps.finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.attackMonster(attacker, 0, 10));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(drain(attacker)));
        assertEquals(10L, attacker.player().hp());

        clock.advanceMillis(1_601L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
        assertEquals(10L, attacker.player().hp());

        assertTrue(maps.attackMonster(attacker, 0, 500));
        drain(attacker);
        clock.advanceMillis(9_000L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(drain(attacker)));
        clock.advanceMillis(1L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(), drain(attacker));
    }

    @Test
    void closedMemberDoesNotReceiveRespawnPacket() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.finishLoad(attacker);
        maps.finishLoad(peer);
        drain(attacker);
        drain(peer);
        assertTrue(maps.attackMonster(attacker, 0, 500));
        drain(attacker);
        drain(peer);
        peer.close();
        drain(attacker);

        clock.advanceMillis(8_001L);
        maps.tickMonsterLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(drain(attacker)));
        assertEquals(List.of(), drain(peer));
    }

    @Test
    void emptyRetainedZoneContinuesRespawnLifecycle() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.attackMonster(attacker, 0, 500));
        drain(attacker);
        maps.leave(attacker);

        assertEquals(0, maps.memberCount(1, 0));
        assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());
        clock.advanceMillis(9_001L);
        maps.tickMonsterLifecycle();
        MonsterSnapshot respawned = maps.monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, respawned.hp());
        assertEquals(0, respawned.status());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void respawnedMonsterReentersExistingCombatFlow() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        MapService maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.finishLoad(attacker);
        drain(attacker);
        maps.attackMonster(attacker, 0, 500);
        drain(attacker);
        clock.advanceMillis(9_001L);
        maps.tickMonsterLifecycle();
        drain(attacker);

        assertTrue(maps.canTargetMonster(attacker, 0));
        assertTrue(maps.attackMonster(attacker, 0, 10));
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(0, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
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

    private static void attackAtBarrier(CyclicBarrier start, MapService maps,
                                        Session session, AtomicBoolean result,
                                        AtomicReference<Throwable> failure) {
        try {
            start.await();
            result.set(maps.attackMonster(session, 0, 10));
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static MonsterRuntimeFactory monsterFactory() {
        return new MonsterRuntimeFactory(
                ResourceService.fromFrameRoot(
                        Path.of("resources", "json")));
    }

    private static MapService mapsWithMonsters() {
        return new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                monsterFactory());
    }

    private static MapService mapsWithMonsters(Clock clock) {
        return new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                monsterFactory(),
                clock);
    }

    private static MapService mapsWithMonsters(Clock clock, java.util.random.RandomGenerator random) {
        return new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                monsterFactory(),
                clock,
                random);
    }

    private static MapService mapsWithoutMonsters() {
        ResourceService resources = ResourceService.unavailable();
        return new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
    }

    private static int zoneRegistrySize(MapService maps) {
        try {
            Field field = MapService.class.getDeclaredField("zones");
            field.setAccessible(true);
            return ((java.util.Map<?, ?>) field.get(maps)).size();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("unable to inspect zone registry", exception);
        }
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
