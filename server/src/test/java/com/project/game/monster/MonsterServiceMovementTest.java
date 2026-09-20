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
import com.project.game.testsupport.MutableClock;
import com.project.game.testsupport.GameplayServices;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.project.game.testsupport.GameplayTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MonsterServiceMovementTest {

    @Test
    void idleMonsterPatrolIsServerAuthoritative() {
        GameplayServices maps = mapsWithMonsters();
        maps.monsterService().monsterSnapshots(1, 0);
        Zone zone = zoneFor(maps, 1, 0);

        MonsterSnapshot before = zone.monsterSnapshots().getFirst();
        List<Monster.Move> moves = zone.moveMonsters();
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
        List<Monster.Move> moves = zone.moveMonsters();
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
        Monster.Move move = zone.moveMonsters().stream()
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
        Monster.Move move = exactRangeZone.moveMonsters().stream()
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
        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        assertTrue(monster.hasEnemy(player.player().id()));
        zone.moveMonsters();
        assertEquals(979, monster.snapshot().x());
        assertTrue(monster.hasEnemy(player.player().id()));

        assertTrue(maps.mapService().movePlayer(player, 1975, 936));
        drain(player);
        Monster.Move resumed = zone.moveMonsters().stream()
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

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1200);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        Monster.Move returning = zone.moveMonsters().stream()
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

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        Monster.Move chase = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(1075, chase.x());
        assertEquals(1, chase.dir());

        assertTrue(maps.mapService().movePlayer(player, 2176, 936));
        drain(player);

        Monster.Move returning = zone.moveMonsters().stream()
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

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1300);
        setIntField(monster, "moveDir", 1);

        Zone zone = zoneFor(maps, 1, 0);
        Monster.Move move = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 0)
                .findFirst()
                .orElseThrow();

        assertEquals(1296, move.x());
        assertEquals(936, move.y());
        assertEquals(-1, move.dir());
        assertTrue(monster.hasEnemy(player.player().id()));
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
}
