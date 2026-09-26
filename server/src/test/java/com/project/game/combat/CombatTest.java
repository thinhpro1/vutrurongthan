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
import com.project.game.testsupport.MutableClock;
import com.project.game.testsupport.GameplayServices;
import com.project.game.service.AreaService;
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

class CombatTest {
    @Test
    void targetingAndAttackingDoNotCreateAbsentZones() {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        AreaService area = new AreaService(playerPackets, new MonsterPacketWriter());
        MapManager zones = new MapManager(
                com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                new MonsterManager(GameResources.unavailable()), area);
        Combat combat = new Combat(area, playerPackets);

        assertFalse(combat.canTargetMonster(null, 101));
        assertFalse(combat.attackMonster(null, 101));
        assertNull(zones.findMap(1).findZone(3));
    }

    @Test
    void combatCannotCreateZone() {
        GameplayServices maps = mapsWithMonsters();
        Session session = session(player(1, 1, 0), maps);

        assertEquals(4, zoneRegistrySize(maps));
        assertFalse(maps.combat().canTargetMonster(session, 101));
        assertFalse(maps.combat().attackMonster(session, 101));
        assertEquals(4, zoneRegistrySize(maps));
    }

    @Test
    void mapInfoCreatedButPreFinishSessionCannotCombat() {
        GameplayServices maps = mapsWithMonsters();
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        Session session = session(player(1, 1, 0), maps);

        assertEquals(0, maps.memberCount(1, 0));
        assertFalse(maps.combat().canTargetMonster(session, 101));
        assertFalse(maps.combat().attackMonster(session, 101));
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void deadPlayerCannotTargetOrAttackMonster() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        maps.mapManager().finishLoad(dead);
        drain(dead);

        assertFalse(maps.combat().canTargetMonster(dead, 101));
        assertFalse(maps.combat().attackMonster(dead, 101));
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(List.of(), drain(dead));
    }

    @Test
    void postFinishAttackSendsAuthoritativeInjureToAttacker() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        assertTrue(maps.combat().canTargetMonster(attacker, 101));
        assertTrue(maps.combat().attackMonster(attacker, 101));
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(101, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void nonKillingHitDoesNotAwardPotential() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        long powerBefore = attacker.player().power();
        long potentialBefore = attacker.player().potential();

        assertTrue(maps.combat().attackMonster(attacker, 101));

        assertEquals(powerBefore, attacker.player().power());
        assertEquals(potentialBefore, attacker.player().potential());
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
    }

    @Test
    void killingHitAwardsConfiguredPotentialOnlyToKiller() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        long powerBefore = attacker.player().power();
        long potentialBefore = attacker.player().potential();

        killMonster(maps.combat(), attacker);

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

        Player nearMax = player(1, 1, 0);
        nearMax.addPotential(Long.MAX_VALUE - 5L - nearMax.potential());
        Session attacker = session(nearMax, maps);

        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        killMonster(maps.combat(), attacker);

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
        maps.mapManager().finishLoad(killer);
        maps.mapManager().finishLoad(observer);
        drain(killer);
        drain(observer);

        long observerPotential = observer.player().potential();

        for (int hit = 0; hit < 29; hit++) {
            assertTrue(maps.combat().attackMonster(killer, 101));
            drain(killer);
            drain(observer);
        }
        assertTrue(maps.combat().attackMonster(killer, 101));

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
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        killMonster(maps.combat(), attacker);
        drain(attacker);
        long afterKill = attacker.player().potential();

        assertFalse(maps.combat().attackMonster(attacker, 101));

        assertEquals(afterKill, attacker.player().potential());
        assertEquals(List.of(), drain(attacker));
    }

    @Test
    void respawnedMonsterCanAwardPotentialOnANewKill() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        long before = attacker.player().potential();

        killMonster(maps.combat(), attacker);
        drain(attacker);
        assertEquals(before + 10L, attacker.player().potential());

        clock.advanceMillis(9_001L);
        maps.monsterManager().update(maps.mapManager());
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));

        killMonster(maps.combat(), attacker);
        assertEquals(before + 20L, attacker.player().potential());
        assertEquals(
                List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(drain(attacker)));
    }

    @Test
    void movementAndMapChangePreserveRewardedPotential() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        drain(attacker);

        killMonster(maps.combat(), attacker);
        drain(attacker);
        long rewarded = attacker.player().potential();

        assertTrue(maps.movePlayer(attacker, 1260, 640));
        assertEquals(rewarded, attacker.player().potential());

        assertTrue(maps.movePlayer(attacker, 0, 1008));
        assertNotNull(maps.mapManager().changeMap(attacker));

        assertEquals(rewarded, attacker.player().potential());
        assertEquals(rewarded, attacker.player().potential());
    }

    @Test
    void sameZoneReceivesIdenticalCombatBroadcast() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(peer);
        drain(attacker);
        drain(peer);

        assertTrue(maps.combat().attackMonster(attacker, 101));
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
        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(otherZone);
        drain(attacker);
        drain(otherZone);

        assertTrue(maps.combat().attackMonster(attacker, 101));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(attacker)));
        assertEquals(List.of(), drain(otherZone));
        assertEquals(300L, maps.monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void concurrentLethalAttacksProduceOneDeathBroadcast() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session first = session(player(1, 1, 0), maps);
        Session second = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);
        drain(first);
        drain(second);
        for (int i = 0; i < 29; i++) {
            assertTrue(maps.combat().attackMonster(first, 101));
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
        assertEquals(0L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());
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

    private static void killMonster(Combat combat, Session session) throws Exception {
        for (int hit = 0; hit < 29; hit++) {
            assertTrue(combat.attackMonster(session, 101));
            drain(session);
        }
        assertTrue(combat.attackMonster(session, 101));
    }
}
