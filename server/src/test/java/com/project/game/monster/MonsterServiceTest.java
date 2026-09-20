package com.project.game.monster;

import com.project.game.map.*;
import com.project.game.combat.*;
import com.project.game.network.*;
import com.project.game.network.message.*;
import com.project.game.network.packet.*;
import com.project.game.network.codec.*;
import com.project.game.network.transport.*;
import com.project.game.player.*;
import com.project.game.account.*;
import com.project.game.resource.*;
import com.project.game.service.*;
import com.project.game.test.MutableClock;
import com.project.game.testsupport.GameplayServices;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.random.RandomGenerator;

import static com.project.game.testsupport.GameplayTestSupport.*;
import com.project.game.testsupport.GameplayTestSupport.BlockingOfferQueue;
import com.project.game.testsupport.GameplayTestSupport.BlockingRandom;
import static org.junit.jupiter.api.Assertions.*;

class MonsterServiceTest {
    @Test
    void snapshotsCreateRuntimeZoneButLifecycleTickOnlyVisitsExistingZones() {
        ZoneRegistry zones = new ZoneRegistry(new MonsterRuntimeFactory(
                GameResources.fromFrameRoot(Path.of("resources", "json"))));
        MonsterService monsters = new MonsterService(
                zones, new MonsterPacketWriter(), new PlayerPacketWriter());

        assertEquals(0, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(0, zones.snapshot().size());

        assertNotNull(monsters.monsterSnapshots(1, 0));
        assertEquals(1, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(1, zones.snapshot().size());
    }

    @Test
    void monsterSnapshotCreatesZoneWithoutJoiningPlayer() {
        GameplayServices maps = mapsWithMonsters();

        List<MonsterSnapshot> monsters = maps.monsterService().monsterSnapshots(1, 0);

        assertEquals(6, monsters.size());
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5),
                monsters.stream().map(MonsterSnapshot::id).toList());
        assertEquals(0, maps.mapService().memberCount(1, 0));
    }

    @Test
    void mapZeroZoneStartsWithoutRuntimeMonsters() {
        GameplayServices maps = mapsWithMonsters();
        assertTrue(maps.monsterService().monsterSnapshots(0, 0).isEmpty());
        assertEquals(0, maps.mapService().memberCount(0, 0));
    }

    @Test
    void finishLoadReusesZoneCreatedForMonsterSnapshot() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        List<MonsterSnapshot> before = maps.monsterService().monsterSnapshots(1, 0);
        Session joining = session(player(1, 1, 0), maps);

        assertEquals(0, maps.mapService().memberCount(1, 0));
        maps.mapService().finishLoad(joining);

        assertEquals(1, maps.mapService().memberCount(1, 0));
        assertEquals(before, maps.monsterService().monsterSnapshots(1, 0));
    }

    @Test
    void differentMap1ZonesStartWithEquivalentSeeds() {
        GameplayServices maps = mapsWithMonsters();
        List<MonsterSnapshot> zone0 = maps.monsterService().monsterSnapshots(1, 0);
        List<MonsterSnapshot> zone1 = maps.monsterService().monsterSnapshots(1, 1);

        assertEquals(zone0, zone1);
        assertEquals(6, zone0.size());
        assertEquals(6, zone1.size());
    }

    @Test
    void idleMonsterPatrolIsServerAuthoritative() {
        GameplayServices maps = mapsWithMonsters();
        maps.monsterService().monsterSnapshots(1, 0);
        Zone zone = zoneFor(maps, 1, 0);

        MonsterSnapshot before = zone.monsterSnapshots().getFirst();
        List<MonsterMoveResult> moves = zone.moveMonsters();
        MonsterSnapshot after = zone.monsterSnapshots().getFirst();

        assertEquals(975, before.x());
        assertEquals(979, after.x());
        assertEquals(936, after.y());
        assertTrue(moves.stream().anyMatch(result ->
                result.monsterId() == 0
                        && result.x() == 979
                        && result.y() == 936
                        && result.dir() == 1));
    }

    @Test
    void nearbyUnhostilePlayerDoesNotRedirectIdlePatrol() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(player(1, 1, 0).withPosition(100, 936), maps);
        maps.mapService().finishLoad(player);
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        zone.moveMonsters();

        assertEquals(979, zone.monsterSnapshots().getFirst().x());
    }

    @Test
    void successfulDamageMakesMonsterChaseHostilePlayer() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(player(1, 1, 0).withPosition(1900, 936), maps);
        maps.mapService().finishLoad(player);
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        zone.moveMonsters();

        assertTrue(maps.combatService().attackMonster(player, 0, 10));
        drain(player);

        MonsterSnapshot before = zone.monsterSnapshots().getFirst();
        List<MonsterMoveResult> moves = zone.moveMonsters();
        MonsterSnapshot after = zone.monsterSnapshots().getFirst();

        assertEquals(979, before.x());
        assertEquals(983, after.x());
        assertEquals(1, moves.stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow()
                .dir());
    }

    @Test
    void nearestHostilePlayerWinsAndLowerIdBreaksEqualDistance() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session lowerId = session(player(7, 1, 0).withPosition(25, 936), maps);
        Session higherId = session(player(8, 1, 0).withPosition(1925, 936), maps);
        maps.mapService().finishLoad(lowerId);
        maps.mapService().finishLoad(higherId);
        drain(lowerId);
        drain(higherId);

        assertTrue(maps.combatService().attackMonster(lowerId, 0, 10));
        assertTrue(maps.combatService().attackMonster(higherId, 0, 10));
        drain(lowerId);
        drain(higherId);

        Zone zone = zoneFor(maps, 1, 0);
        MonsterMoveResult move = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();

        assertEquals(971, move.x());
        assertEquals(-1, move.dir());
    }

    @Test
    void hostileMonsterStopsInsideAttackRangeButChasesAtExactNineHundred() throws Exception {
        GameplayServices insideRangeMaps = mapsWithMonsters();
        Session insideRange = session(player(1, 1, 0).withPosition(1874, 936), insideRangeMaps);
        insideRangeMaps.mapService().finishLoad(insideRange);
        drain(insideRange);
        assertTrue(insideRangeMaps.combatService().attackMonster(insideRange, 0, 10));
        drain(insideRange);

        Zone insideRangeZone = zoneFor(insideRangeMaps, 1, 0);
        assertTrue(insideRangeZone.moveMonsters().stream()
                .noneMatch(result -> result.monsterId() == 0));
        assertEquals(975, insideRangeZone.monsterSnapshots().getFirst().x());

        GameplayServices exactRangeMaps = mapsWithMonsters();
        Session exactRange = session(player(1, 1, 0).withPosition(1875, 936), exactRangeMaps);
        exactRangeMaps.mapService().finishLoad(exactRange);
        drain(exactRange);
        assertTrue(exactRangeMaps.combatService().attackMonster(exactRange, 0, 10));
        drain(exactRange);

        Zone exactRangeZone = zoneFor(exactRangeMaps, 1, 0);
        MonsterMoveResult move = exactRangeZone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(979, move.x());
    }

    @Test
    void leashDoesNotClearHostilityAndReentryResumesChase() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(player(1, 1, 0).withPosition(-300, 936), maps);
        maps.mapService().finishLoad(player);
        drain(player);
        assertTrue(maps.combatService().attackMonster(player, 0, 10));
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        RuntimeMonster monster = runtimeMonsters(maps, 1, 0).getFirst();
        assertTrue(monster.hasEnemy(player.player().id()));
        zone.moveMonsters();
        assertEquals(979, monster.snapshot().x());
        assertTrue(monster.hasEnemy(player.player().id()));

        assertTrue(maps.mapService().movePlayer(player, 1975, 936));
        drain(player);
        MonsterMoveResult resumed = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(983, resumed.x());
        assertEquals(1, resumed.dir());
    }

    @Test
    void unavailableHostileTargetWalksMonsterBackToPatrolCorridor() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(player(1, 1, 0).withPosition(2200, 936), maps);
        maps.mapService().finishLoad(player);
        drain(player);
        assertTrue(maps.combatService().attackMonster(player, 0, 10));
        drain(player);

        RuntimeMonster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1200);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        MonsterMoveResult returning = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(1196, returning.x());
        assertEquals(-1, returning.dir());
        assertTrue(monster.hasEnemy(player.player().id()));
    }

    @Test
    void chaseBoundaryTransitionReturnsInwardImmediatelyWhenLeashEnds() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(player(1, 1, 0).withPosition(1971, 936), maps);
        maps.mapService().finishLoad(player);
        drain(player);

        assertTrue(maps.combatService().attackMonster(player, 0, 10));
        drain(player);

        RuntimeMonster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        MonsterMoveResult chase = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(1075, chase.x());
        assertEquals(1, chase.dir());

        assertTrue(maps.mapService().movePlayer(player, 2176, 936));
        drain(player);

        MonsterMoveResult returning = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(1071, returning.x());
        assertEquals(-1, returning.dir());
        assertTrue(monster.hasEnemy(player.player().id()));
    }

    @Test
    void hostilePlayerJustOutsideLeashDoesNotPinReturningMonster() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(
                player(1, 1, 0).withPosition(2176, 936),
                maps);
        maps.mapService().finishLoad(player);
        drain(player);

        assertTrue(maps.combatService().attackMonster(player, 0, 10));
        drain(player);

        RuntimeMonster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1300);
        setIntField(monster, "moveDir", 1);

        Zone zone = zoneFor(maps, 1, 0);
        MonsterMoveResult move = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();

        assertEquals(1296, move.x());
        assertEquals(936, move.y());
        assertEquals(-1, move.dir());
        assertTrue(monster.hasEnemy(player.player().id()));
    }

    @Test
    void concurrentMonsterSnapshotsRemainStableForSameZone() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<List<MonsterSnapshot>> first = new AtomicReference<>();
        AtomicReference<List<MonsterSnapshot>> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread one = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                first.set(maps.monsterService().monsterSnapshots(1, 0));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });

        Thread two = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                second.set(maps.monsterService().monsterSnapshots(1, 0));
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
        assertEquals(0, maps.mapService().memberCount(1, 0));
    }

    @Test
    void respawnTickDoesNotCreateZones() {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);

        assertEquals(0, zoneRegistrySize(maps));
        maps.monsterService().tickLifecycle();
        assertEquals(0, zoneRegistrySize(maps));
    }

    @Test
    void onePlayerMonsterRespawnsOnlyAfterNineSecondDeadline() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapService().finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 500));
        assertEquals(List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(drain(attacker)));

        clock.advanceMillis(9_000L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(1, maps.monsterService().monsterSnapshots(1, 0).getFirst().status());

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        List<Message> messages = withoutMonsterMoves(drain(attacker));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(300L, reader.readLong());
        assertEquals(0, reader.remaining());
        MonsterSnapshot snapshot = maps.monsterService().monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, snapshot.hp());
        assertEquals(0, snapshot.status());
    }

    @Test
    void sameZoneMembersReceiveOneRespawnBroadcastEach() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(peer);
        drain(attacker);
        drain(peer);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 500));
        drain(attacker);
        drain(peer);

        clock.advanceMillis(8_001L);
        maps.monsterService().tickLifecycle();
        List<Message> attackerMessages = withoutMonsterMoves(drain(attacker));
        List<Message> peerMessages = withoutMonsterMoves(drain(peer));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(peerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), peerMessages.getFirst().payload());

        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(List.of(), withoutMonsterMoves(drain(peer)));
    }

    @Test
    void crossZoneDoesNotReceiveRespawnBroadcast() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session other = session(player(2, 1, 1), maps);

        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(other);
        drain(attacker);
        drain(other);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 500));
        drain(attacker);

        clock.advanceMillis(9_001L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(List.of(), withoutMonsterMoves(drain(other)));
        assertEquals(300L, maps.monsterService().monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void standingNearMonsterDoesNotAutoAggro() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session player = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(player);
        drain(player);

        clock.advanceMillis(5_000L);
        maps.monsterService().tickLifecycle();

        assertEquals(List.of(), withoutMonsterMoves(drain(player)));
        assertEquals(100L, player.player().hp());
    }

    @Test
    void monsterLifecycleBroadcastsIdenticalMovementToZoneMembers() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session first = session(player(1, 1, 0), maps);
        Session second = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);
        drain(first);
        drain(second);

        maps.monsterService().tickLifecycle();

        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(6, firstMessages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .count());
        assertEquals(6, secondMessages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .count());
        List<Message> firstMoves = firstMessages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .toList();
        List<Message> secondMoves = secondMessages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .toList();
        for (int i = 0; i < firstMoves.size(); i++) {
            assertArrayEquals(firstMoves.get(i).payload(), secondMoves.get(i).payload());
        }

        assertMonsterMove(firstMessages.getFirst(), 0, 979, 936, 1);
        assertMonsterMove(secondMessages.getFirst(), 0, 979, 936, 1);
    }

    @Test
    void monsterMovementIsBroadcastBeforeSameTickAttack() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(player(1, 1, 0).withPosition(1875, 936), maps);
        maps.mapService().finishLoad(target);
        drain(target);
        assertTrue(maps.combatService().attackMonster(target, 0, 10));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        List<Message> messages = drain(target);
        assertEquals(MessageName.MONSTER_MOVE, messages.getFirst().command());
        assertMonsterMove(messages.getFirst(), 0, 979, 936, 1);
        assertEquals(MessageName.MONSTER_ATTACK, messages.get(6).command());
        assertEquals(90L, target.player().hp());
    }

    @Test
    void hostileMonsterAlreadyInsideAttackRangeDoesNotMoveThatTick() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(player(1, 1, 0).withPosition(1874, 936), maps);
        maps.mapService().finishLoad(target);
        drain(target);
        assertTrue(maps.combatService().attackMonster(target, 0, 10));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        List<Message> messages = drain(target);
        assertTrue(messages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .noneMatch(message -> monsterMoveId(message) == 0));
        assertTrue(messages.stream()
                .anyMatch(message -> message.command() == MessageName.MONSTER_ATTACK));
    }

    @Test
    void retaliationBroadcastsToSameZoneAndMutatesOnlyTargetHp() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(observer);
        drain(attacker);
        drain(observer);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        drain(attacker);
        drain(observer);
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        List<Message> attackerMessages = withoutMonsterMoves(drain(attacker));
        List<Message> observerMessages = withoutMonsterMoves(drain(observer));
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_ATTACK), commands(observerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), observerMessages.getFirst().payload());
        assertEquals(90L, attacker.player().hp());
        assertEquals(100L, observer.player().hp());
    }

    @Test
    void monsterRetaliationCanKillPlayerAtExactDamage() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(player(1, 1, 0).withHp(10), maps);
        maps.mapService().finishLoad(target);
        drain(target);

        assertTrue(maps.combatService().attackMonster(target, 0, 10L));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        assertEquals(0L, target.player().hp());
    }

    @Test
    void monsterRetaliationClampsOverkillToZero() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        PlayerProfile lowHp = player(1, 1, 0).withHp(5);
        Session target = session(lowHp, maps);
        maps.mapService().finishLoad(target);
        drain(target);

        assertTrue(maps.combatService().attackMonster(target, 0, 10L));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        assertEquals(0L, target.player().hp());
    }

    @Test
    void lethalRetaliationClearsVictimHostilityFromEveryMonster() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(player(1, 1, 0).withHp(10), maps);
        maps.mapService().finishLoad(target);
        drain(target);

        for (int monsterId = 0; monsterId < 6; monsterId++) {
            assertTrue(maps.combatService().attackMonster(target, monsterId, 1L));
            drain(target);
        }

        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        assertEquals(0L, target.player().hp());
        assertTrue(runtimeMonsters(maps, 1, 0).stream()
                .noneMatch(monster -> monster.hasEnemy(target.player().id())));

        withoutMonsterMoves(drain(target));
        clock.advanceMillis(10_000L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(target)));
    }

    @Test
    void lethalMonsterAttackBroadcastsSelfAndObserverDeathAfterAttack() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session victim = session(player(1, 1, 0).withHp(10), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(victim);
        maps.mapService().finishLoad(observer);
        drain(victim);
        drain(observer);

        assertTrue(maps.combatService().attackMonster(victim, 0, 10L));
        drain(victim);
        drain(observer);
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        List<Message> victimMessages = withoutMonsterMoves(drain(victim));
        assertEquals(
                List.of(MessageName.MONSTER_ATTACK, MessageName.ME_DIE),
                commands(victimMessages));
        var selfDeath = victimMessages.get(1).reader();
        assertEquals(victim.player().x(), selfDeath.readShort());
        assertEquals(victim.player().y(), selfDeath.readShort());
        assertEquals(0, selfDeath.remaining());

        List<Message> observerMessages = withoutMonsterMoves(drain(observer));
        assertEquals(
                List.of(MessageName.MONSTER_ATTACK, MessageName.PLAYER_DIE),
                commands(observerMessages));
        var observedDeath = observerMessages.get(1).reader();
        assertEquals(victim.player().id(), observedDeath.readInt());
        assertEquals(victim.player().x(), observedDeath.readShort());
        assertEquals(victim.player().y(), observedDeath.readShort());
        assertEquals(0, observedDeath.remaining());
    }

    @Test
    void retaliationDoesNotCrossZones() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        Session otherZone = session(player(2, 1, 1), maps);
        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();

        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(List.of(), withoutMonsterMoves(drain(otherZone)));
        assertEquals(100L, otherZone.player().hp());
    }

    @Test
    void retaliationCooldownIsStrictAndRangeUsesCurrentPosition() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        drain(attacker);

        attacker.bindPlayer(attacker.player().withPosition(975 + 901, 936));
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));

        attacker.bindPlayer(attacker.player().withPosition(975, 936));
        clock.advanceMillis(1_600L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(80L, attacker.player().hp());
    }

    @Test
    void retaliationKillsAtZeroAndRespawnRequiresNewHit() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        attacker.bindPlayer(attacker.player().withHp(20));
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(10L, attacker.player().hp());

        clock.advanceMillis(1_601L);
        maps.monsterService().tickLifecycle();
        List<Message> lethalMessages = drain(attacker);
        assertEquals(List.of(
                        MessageName.MONSTER_MOVE,
                        MessageName.MONSTER_MOVE,
                        MessageName.MONSTER_MOVE,
                        MessageName.MONSTER_MOVE,
                        MessageName.MONSTER_MOVE,
                        MessageName.MONSTER_ATTACK,
                        MessageName.ME_DIE),
                commands(lethalMessages));
        assertTrue(lethalMessages.subList(0, 5).stream()
                .noneMatch(message -> monsterMoveId(message) == 0));
        assertEquals(0L, attacker.player().hp());

        clock.advanceMillis(1_601L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(0L, attacker.player().hp());

        maps.mapService().leave(attacker);
        Session survivor = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(survivor);
        drain(survivor);

        assertTrue(maps.combatService().attackMonster(survivor, 0, 500));
        drain(survivor);
        clock.advanceMillis(9_000L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(survivor)));
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(survivor))));
        clock.advanceMillis(1L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(), withoutMonsterMoves(drain(survivor)));
    }

    @Test
    void closedMemberDoesNotReceiveRespawnPacket() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(peer);
        drain(attacker);
        drain(peer);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 500));
        drain(attacker);
        drain(peer);
        peer.close();
        drain(attacker);

        clock.advanceMillis(8_001L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(List.of(), withoutMonsterMoves(drain(peer)));
    }

    @Test
    void emptyRetainedZoneContinuesRespawnLifecycle() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapService().finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.combatService().attackMonster(attacker, 0, 500));
        drain(attacker);
        maps.mapService().leave(attacker);

        assertEquals(0, maps.mapService().memberCount(1, 0));
        assertEquals(1, maps.monsterService().monsterSnapshots(1, 0).getFirst().status());
        clock.advanceMillis(9_001L);
        maps.monsterService().tickLifecycle();
        MonsterSnapshot respawned = maps.monsterService().monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, respawned.hp());
        assertEquals(0, respawned.status());
        assertEquals(0, maps.mapService().memberCount(1, 0));
    }

    @Test
    void respawnedMonsterReentersExistingCombatFlow() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapService().finishLoad(attacker);
        drain(attacker);
        maps.combatService().attackMonster(attacker, 0, 500);
        drain(attacker);
        clock.advanceMillis(9_001L);
        maps.monsterService().tickLifecycle();
        drain(attacker);

        assertTrue(maps.combatService().canTargetMonster(attacker, 0));
        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(0, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }
}
