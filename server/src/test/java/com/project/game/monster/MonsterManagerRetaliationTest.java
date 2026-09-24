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
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.project.game.testsupport.GameplayTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MonsterManagerRetaliationTest {

    @Test
    void retaliationBroadcastsToSameZoneAndMutatesOnlyTargetHp() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Session attacker = session(player(1, 1, 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(observer);
        drain(attacker);
        drain(observer);

        assertTrue(maps.combatService().attackMonster(attacker, 101));
        drain(attacker);
        drain(observer);
        clock.advanceMillis(1L);
        maps.monsterManager().update();

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
        Session target = session(hp(player(1, 1, 0), 10), maps);
        maps.mapManager().finishLoad(target);
        drain(target);

        assertTrue(maps.combatService().attackMonster(target, 101));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterManager().update();

        assertEquals(0L, target.player().hp());
    }

    @Test
    void monsterRetaliationClampsOverkillToZero() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Player lowHp = hp(player(1, 1, 0), 5);
        Session target = session(lowHp, maps);
        maps.mapManager().finishLoad(target);
        drain(target);

        assertTrue(maps.combatService().attackMonster(target, 101));
        drain(target);

        clock.advanceMillis(1L);
        maps.monsterManager().update();

        assertEquals(0L, target.player().hp());
    }

    @Test
    void lethalRetaliationClearsVictimHostilityFromEveryMonster() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session target = session(hp(player(1, 1, 0), 10), maps);
        maps.mapManager().finishLoad(target);
        drain(target);

        for (int monsterId = 101; monsterId <= 106; monsterId++) {
            assertTrue(maps.combatService().attackMonster(target, monsterId));
            drain(target);
        }

        clock.advanceMillis(1L);
        maps.monsterManager().update();

        assertEquals(0L, target.player().hp());
        assertTrue(runtimeMonsters(maps, 1, 0).stream()
                .noneMatch(monster -> monster.hasEnemy(target.player().id())));

        withoutMonsterMoves(drain(target));
        clock.advanceMillis(10_000L);
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(target)));
    }

    @Test
    void lethalMonsterAttackBroadcastsSelfAndObserverDeathAfterAttack() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(0));
        Session victim = session(hp(player(1, 1, 0), 10), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(victim);
        maps.mapManager().finishLoad(observer);
        drain(victim);
        drain(observer);

        assertTrue(maps.combatService().attackMonster(victim, 101));
        drain(victim);
        drain(observer);
        clock.advanceMillis(1L);
        maps.monsterManager().update();

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
        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.combatService().attackMonster(attacker, 101));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.monsterManager().update();

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
        maps.mapManager().finishLoad(attacker);
        drain(attacker);
        assertTrue(maps.combatService().attackMonster(attacker, 101));
        drain(attacker);

        attacker.player().changeMap(1, 0, 975 + 901, 936);
        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));

        attacker.player().changeMap(1, 0, 975, 936);
        clock.advanceMillis(1_600L);
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(80L, attacker.player().hp());
    }

    @Test
    void retaliationKillsAtZeroAndRespawnRequiresNewHit() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(12345L));
        Player attackerPlayer = TestPlayers.initial(1L, 1, "player1", 0);
        attackerPlayer.changeMap(1, 0, 1250, 648);
        attackerPlayer.injure(180);
        Session attacker = session(attackerPlayer, maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combatService().attackMonster(attacker, 101));
        drain(attacker);
        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_ATTACK),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(10L, attacker.player().hp());

        clock.advanceMillis(1_601L);
        maps.monsterManager().update();
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
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(0L, attacker.player().hp());

        maps.mapManager().leave(attacker);
        Session survivor = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(survivor);
        drain(survivor);

        for (int hit = 0; hit < 28; hit++) {
            assertTrue(maps.combatService().attackMonster(survivor, 101));
            drain(survivor);
        }
        assertTrue(maps.combatService().attackMonster(survivor, 101));
        drain(survivor);
        clock.advanceMillis(9_000L);
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(survivor)));
        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(survivor))));
        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(survivor)));
    }
}
