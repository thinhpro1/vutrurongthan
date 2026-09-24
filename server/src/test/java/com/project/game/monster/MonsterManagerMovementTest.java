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

class MonsterManagerMovementTest {

    @Test
    void idleMonsterPatrolIsServerAuthoritative() {
        GameplayServices maps = mapsWithMonsters();
        maps.monsterManager().monsterSnapshots(1, 0);
        Zone zone = zoneFor(maps, 1, 0);

        MonsterSnapshot before = zone.monsterSnapshots().getFirst();
        List<Monster.Move> moves = zone.moveMonsters();
        MonsterSnapshot after = zone.monsterSnapshots().getFirst();

        assertEquals(975, before.x());
        assertEquals(979, after.x());
        assertEquals(936, after.y());
        assertTrue(moves.stream().anyMatch(result ->
                result.monsterId() == 101
                        && result.x() == 979
                        && result.y() == 936
                        && result.dir() == 1));
    }

    @Test
    void nearbyUnhostilePlayerDoesNotRedirectIdlePatrol() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(at(player(1, 1, 0), 100, 936), maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        zone.moveMonsters();

        assertEquals(979, zone.monsterSnapshots().getFirst().x());
    }

    @Test
    void successfulDamageMakesMonsterChaseHostilePlayer() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(at(player(1, 1, 0), 1900, 936), maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        zone.moveMonsters();

        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);

        MonsterSnapshot before = zone.monsterSnapshots().getFirst();
        List<Monster.Move> moves = zone.moveMonsters();
        MonsterSnapshot after = zone.monsterSnapshots().getFirst();

        assertEquals(979, before.x());
        assertEquals(983, after.x());
        assertEquals(1, moves.stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow()
                .dir());
    }

    @Test
    void nearestHostilePlayerWinsAndLowerIdBreaksEqualDistance() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session lowerId = session(at(player(7, 1, 0), 25, 936), maps);
        Session higherId = session(at(player(8, 1, 0), 1925, 936), maps);
        maps.mapManager().finishLoad(lowerId);
        maps.mapManager().finishLoad(higherId);
        drain(lowerId);
        drain(higherId);

        assertTrue(maps.combat().attackMonster(lowerId, 101));
        assertTrue(maps.combat().attackMonster(higherId, 101));
        drain(lowerId);
        drain(higherId);

        Zone zone = zoneFor(maps, 1, 0);
        Monster.Move move = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow();

        assertEquals(971, move.x());
        assertEquals(-1, move.dir());
    }

    @Test
    void hostileMonsterStopsInsideAttackRangeButChasesAtExactNineHundred() throws Exception {
        GameplayServices insideRangeMaps = mapsWithMonsters();
        Session insideRange = session(at(player(1, 1, 0), 1874, 936), insideRangeMaps);
        insideRangeMaps.mapManager().finishLoad(insideRange);
        drain(insideRange);
        assertTrue(insideRangeMaps.combat().attackMonster(insideRange, 101));
        drain(insideRange);

        Zone insideRangeZone = zoneFor(insideRangeMaps, 1, 0);
        assertTrue(insideRangeZone.moveMonsters().stream()
                .noneMatch(result -> result.monsterId() == 101));
        assertEquals(975, insideRangeZone.monsterSnapshots().getFirst().x());

        GameplayServices exactRangeMaps = mapsWithMonsters();
        Session exactRange = session(at(player(1, 1, 0), 1875, 936), exactRangeMaps);
        exactRangeMaps.mapManager().finishLoad(exactRange);
        drain(exactRange);
        assertTrue(exactRangeMaps.combat().attackMonster(exactRange, 101));
        drain(exactRange);

        Zone exactRangeZone = zoneFor(exactRangeMaps, 1, 0);
        Monster.Move move = exactRangeZone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow();
        assertEquals(979, move.x());
    }

    @Test
    void leashDoesNotClearHostilityAndReentryResumesChase() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(at(player(1, 1, 0), -300, 936), maps);
        maps.mapManager().finishLoad(player);
        drain(player);
        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);

        Zone zone = zoneFor(maps, 1, 0);
        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        assertTrue(monster.hasEnemy(player.player().id()));
        zone.moveMonsters();
        assertEquals(979, monster.snapshot().x());
        assertTrue(monster.hasEnemy(player.player().id()));

        assertTrue(maps.mapManager().movePlayer(player, 1975, 936));
        drain(player);
        Monster.Move resumed = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow();
        assertEquals(983, resumed.x());
        assertEquals(1, resumed.dir());
    }

    @Test
    void unavailableHostileTargetWalksMonsterBackToPatrolCorridor() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(at(player(1, 1, 0), 2200, 936), maps);
        maps.mapManager().finishLoad(player);
        drain(player);
        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1200);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        Monster.Move returning = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow();
        assertEquals(1196, returning.x());
        assertEquals(-1, returning.dir());
        assertTrue(monster.hasEnemy(player.player().id()));
    }

    @Test
    void chaseBoundaryTransitionReturnsInwardImmediatelyWhenLeashEnds() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session player = session(at(player(1, 1, 0), 1971, 936), maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);
        Zone zone = zoneFor(maps, 1, 0);

        Monster.Move chase = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
                .findFirst()
                .orElseThrow();
        assertEquals(1075, chase.x());
        assertEquals(1, chase.dir());

        assertTrue(maps.mapManager().movePlayer(player, 2176, 936));
        drain(player);

        Monster.Move returning = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
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
                at(player(1, 1, 0), 2176, 936),
                maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);

        Monster monster = runtimeMonsters(maps, 1, 0).getFirst();
        setIntField(monster, "x", 1300);
        setIntField(monster, "moveDir", 1);

        Zone zone = zoneFor(maps, 1, 0);
        Monster.Move move = zone.moveMonsters().stream()
                .filter(result -> result.monsterId() == 101)
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
        maps.mapManager().finishLoad(player);
        drain(player);

        clock.advanceMillis(5_000L);
        maps.monsterManager().update();

        assertEquals(List.of(), withoutMonsterMoves(drain(player)));
        assertEquals(100L, player.player().hp());
    }

    @Test
    void monsterLifecycleBroadcastsIdenticalMovementToZoneMembers() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session first = session(player(1, 1, 0), maps);
        Session second = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);
        drain(first);
        drain(second);

        maps.monsterManager().update();

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

        assertMonsterMove(firstMessages.getFirst(), 101, 979, 936, 1);
        assertMonsterMove(secondMessages.getFirst(), 101, 979, 936, 1);
    }

    @Test
    void monsterMovementIsBroadcastBeforeSameTickAttack() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(at(player(1, 1, 0), 1875, 936), maps);
        maps.mapManager().finishLoad(target);
        drain(target);
        assertTrue(maps.combat().attackMonster(target, 101));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterManager().update();

        List<Message> messages = drain(target);
        assertEquals(MessageName.MONSTER_MOVE, messages.getFirst().command());
        assertMonsterMove(messages.getFirst(), 101, 979, 936, 1);
        assertEquals(MessageName.MONSTER_ATTACK, messages.get(6).command());
        assertEquals(90L, target.player().hp());
    }

    @Test
    void hostileMonsterAlreadyInsideAttackRangeDoesNotMoveThatTick() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(at(player(1, 1, 0), 1874, 936), maps);
        maps.mapManager().finishLoad(target);
        drain(target);
        assertTrue(maps.combat().attackMonster(target, 101));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterManager().update();

        List<Message> messages = drain(target);
        assertTrue(messages.stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .noneMatch(message -> monsterMoveId(message) == 0));
        assertTrue(messages.stream()
                .anyMatch(message -> message.command() == MessageName.MONSTER_ATTACK));
    }
}
