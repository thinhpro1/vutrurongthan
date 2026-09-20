package com.project.game.combat;

import com.project.game.map.*;
import com.project.game.monster.*;
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

class CombatServiceTest {
    @Test
    void targetingAndAttackingDoNotCreateAbsentZones() {
        ZoneRegistry zones = new ZoneRegistry(new MonsterRuntimeFactory(GameResources.unavailable()));
        CombatService combat = new CombatService(
                zones, new PlayerPacketWriter(), new MonsterPacketWriter());

        assertFalse(combat.canTargetMonster(null, 0));
        assertFalse(combat.attackMonster(null, 0, 1L));
        assertNull(zones.find(1, 0));
    }

    @Test
    void combatCannotCreateZone() {
        GameplayServices maps = mapsWithMonsters();
        Session session = session(player(1, 1, 0), maps);

        assertEquals(0, zoneRegistrySize(maps));
        assertFalse(maps.combatService().canTargetMonster(session, 0));
        assertFalse(maps.combatService().attackMonster(session, 0, 10));
        assertEquals(0, zoneRegistrySize(maps));
    }

    @Test
    void mapInfoCreatedButPreFinishSessionCannotCombat() {
        GameplayServices maps = mapsWithMonsters();
        assertEquals(300L, maps.monsterService().monsterSnapshots(1, 0).getFirst().hp());
        Session session = session(player(1, 1, 0), maps);

        assertEquals(0, maps.mapService().memberCount(1, 0));
        assertFalse(maps.combatService().canTargetMonster(session, 0));
        assertFalse(maps.combatService().attackMonster(session, 0, 10));
        assertEquals(300L, maps.monsterService().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void deadPlayerCannotTargetOrAttackMonster() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session dead = session(player(1, 1, 0).withHp(0), maps);
        maps.mapService().finishLoad(dead);
        drain(dead);

        assertFalse(maps.combatService().canTargetMonster(dead, 0));
        assertFalse(maps.combatService().attackMonster(dead, 0, 10L));
        assertEquals(300L, maps.monsterService().monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(List.of(), drain(dead));
    }

    @Test
    void postFinishAttackSendsAuthoritativeInjureToAttacker() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
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

    @Test
    void nonKillingHitDoesNotAwardPotential() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        long powerBefore = attacker.player().power();
        long potentialBefore = attacker.player().potential();

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10L));

        assertEquals(powerBefore, attacker.player().power());
        assertEquals(potentialBefore, attacker.player().potential());
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
    }

    @Test
    void killingHitAwardsConfiguredPotentialOnlyToKiller() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        long powerBefore = attacker.player().power();
        long potentialBefore = attacker.player().potential();

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));

        assertEquals(powerBefore, attacker.player().power());
        assertEquals(potentialBefore + 10L, attacker.player().potential());

        List<Message> messages = drain(attacker);
        assertEquals(
                List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(messages));

        var reward = messages.get(1).reader();
        assertEquals(62, reward.readByte());
        assertEquals(potentialBefore + 10L, reward.readLong());
        assertEquals(0, reward.remaining());
    }

    @Test
    void killingRewardSaturatesPotentialInsteadOfOverflowing() throws Exception {
        GameplayServices maps = mapsWithMonsters();

        PlayerProfile nearMax = player(1, 1, 0)
                .withPotential(Long.MAX_VALUE - 5L);
        Session attacker = session(nearMax, maps);

        maps.mapService().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));

        assertEquals(Long.MAX_VALUE, attacker.player().potential());
        assertEquals(nearMax.power(), attacker.player().power());

        List<Message> messages = drain(attacker);
        assertEquals(
                List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(messages));

        var reward = messages.get(1).reader();
        assertEquals(62, reward.readByte());
        assertEquals(Long.MAX_VALUE, reward.readLong());
        assertEquals(0, reward.remaining());
    }

    @Test
    void deathBroadcastReachesObserverButRewardPacketDoesNot() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session killer = session(player(1, 1, 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(killer);
        maps.mapService().finishLoad(observer);
        drain(killer);
        drain(observer);

        long observerPotential = observer.player().potential();

        assertTrue(maps.combatService().attackMonster(killer, 0, 500L));

        assertEquals(
                List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(drain(killer)));
        assertEquals(
                List.of(MessageName.MONSTER_START_DIE),
                commands(drain(observer)));
        assertEquals(observerPotential, observer.player().potential());
    }

    @Test
    void deadMonsterCannotAwardDuplicatePotential() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));
        drain(attacker);
        long afterKill = attacker.player().potential();

        assertFalse(maps.combatService().attackMonster(attacker, 0, 500L));

        assertEquals(afterKill, attacker.player().potential());
        assertEquals(List.of(), drain(attacker));
    }

    @Test
    void respawnedMonsterCanAwardPotentialOnANewKill() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        long before = attacker.player().potential();

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));
        drain(attacker);
        assertEquals(before + 10L, attacker.player().potential());

        clock.advanceMillis(9_001L);
        maps.monsterService().tickLifecycle();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));
        assertEquals(before + 20L, attacker.player().potential());
        assertEquals(
                List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(drain(attacker)));
    }

    @Test
    void movementAndMapChangePreserveRewardedPotential() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 500L));
        drain(attacker);
        long rewarded = attacker.player().potential();

        assertTrue(maps.mapService().movePlayer(attacker, 1260, 640));
        assertEquals(rewarded, attacker.player().potential());

        PlayerProfile moved = attacker.player();
        var changed = maps.mapService().changeMap(
                attacker,
                moved.mapId(),
                moved.zoneId(),
                0,
                0,
                1250,
                648);

        assertTrue(changed.isPresent());
        assertEquals(rewarded, changed.orElseThrow().potential());
        assertEquals(rewarded, attacker.player().potential());
    }

    @Test
    void sameZoneReceivesIdenticalCombatBroadcast() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(peer);
        drain(attacker);
        drain(peer);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        List<Message> attackerMessages = drain(attacker);
        List<Message> peerMessages = drain(peer);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(peerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), peerMessages.getFirst().payload());
    }

    @Test
    void crossZoneDoesNotReceiveCombatBroadcast() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        Session otherZone = session(player(2, 1, 1), maps);
        maps.mapService().finishLoad(attacker);
        maps.mapService().finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.combatService().attackMonster(attacker, 0, 10));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
        assertEquals(List.of(), drain(otherZone));
        assertEquals(300L, maps.monsterService().monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void concurrentLethalAttacksProduceOneDeathBroadcast() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session first = session(player(1, 1, 0), maps);
        Session second = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);
        drain(first);
        drain(second);
        for (int i = 0; i < 29; i++) {
            assertTrue(maps.combatService().attackMonster(first, 0, 10));
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
        Session winner = firstResult.get() ? first : second;
        Session loser = firstResult.get() ? second : first;
        assertEquals(11L, winner.player().potential());
        assertEquals(1L, loser.player().potential());
        assertEquals(0L, maps.monsterService().monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(1, maps.monsterService().monsterSnapshots(1, 0).getFirst().status());
        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(1, commands(firstMessages).stream()
                .filter(command -> command == MessageName.MONSTER_START_DIE).count());
        assertEquals(1, commands(secondMessages).stream()
                .filter(command -> command == MessageName.MONSTER_START_DIE).count());
        long rewardPackets = java.util.stream.Stream.concat(
                        firstMessages.stream(),
                        secondMessages.stream())
                .filter(message -> message.command() == MessageName.PLAYER_INFO)
                .count();
        assertEquals(1L, rewardPackets);
    }
}
