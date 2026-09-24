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

class MonsterManagerRespawnTest {

    @Test
    void respawnTickDoesNotCreateAdditionalZones() {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);

        assertEquals(2, zoneRegistrySize(maps));
        maps.monsterManager().update();
        assertEquals(2, zoneRegistrySize(maps));
    }

    @Test
    void onePlayerMonsterRespawnsOnlyAfterNineSecondDeadline() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapManager().finishLoad(attacker);
        drain(attacker);
        killMonster(maps.combatService(), attacker);
        assertEquals(List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                commands(drain(attacker)));

        clock.advanceMillis(9_000L);
        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(1, maps.monsterManager().monsterSnapshots(1, 0).getFirst().status());

        clock.advanceMillis(1L);
        maps.monsterManager().update();
        List<Message> messages = withoutMonsterMoves(drain(attacker));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(101, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(300L, reader.readLong());
        assertEquals(0, reader.remaining());
        MonsterSnapshot snapshot = maps.monsterManager().monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, snapshot.hp());
        assertEquals(0, snapshot.status());
    }

    @Test
    void sameZoneMembersReceiveOneRespawnBroadcastEach() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(peer);
        drain(attacker);
        drain(peer);
        killMonster(maps.combatService(), attacker, peer);
        drain(attacker);
        drain(peer);

        clock.advanceMillis(8_001L);
        maps.monsterManager().update();
        List<Message> attackerMessages = withoutMonsterMoves(drain(attacker));
        List<Message> peerMessages = withoutMonsterMoves(drain(peer));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(attackerMessages));
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(peerMessages));
        assertArrayEquals(attackerMessages.getFirst().payload(), peerMessages.getFirst().payload());

        maps.monsterManager().update();
        assertEquals(List.of(), withoutMonsterMoves(drain(attacker)));
        assertEquals(List.of(), withoutMonsterMoves(drain(peer)));
    }

    @Test
    void crossZoneDoesNotReceiveRespawnBroadcast() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session other = session(player(2, 1, 1), maps);

        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(other);
        drain(attacker);
        drain(other);
        killMonster(maps.combatService(), attacker);
        drain(attacker);

        clock.advanceMillis(9_001L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(List.of(), withoutMonsterMoves(drain(other)));
        assertEquals(300L, maps.monsterManager().monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void closedMemberDoesNotReceiveRespawnPacket() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);
        Session peer = session(player(2, 1, 0), maps);

        maps.mapManager().finishLoad(attacker);
        maps.mapManager().finishLoad(peer);
        drain(attacker);
        drain(peer);
        killMonster(maps.combatService(), attacker, peer);
        peer.close();
        drain(attacker);

        clock.advanceMillis(8_001L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN),
                commands(withoutMonsterMoves(drain(attacker))));
        assertEquals(List.of(), withoutMonsterMoves(drain(peer)));
    }

    @Test
    void emptyRetainedZoneContinuesRespawnLifecycle() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapManager().finishLoad(attacker);
        drain(attacker);
        killMonster(maps.combatService(), attacker);
        maps.mapManager().leave(attacker);

        assertEquals(0, maps.mapManager().memberCount(1, 0));
        assertEquals(1, maps.monsterManager().monsterSnapshots(1, 0).getFirst().status());
        clock.advanceMillis(9_001L);
        maps.monsterManager().update();
        MonsterSnapshot respawned = maps.monsterManager().monsterSnapshots(1, 0).getFirst();
        assertEquals(300L, respawned.hp());
        assertEquals(0, respawned.status());
        assertEquals(0, maps.mapManager().memberCount(1, 0));
    }

    @Test
    void respawnedMonsterReentersExistingCombatFlow() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock);
        Session attacker = session(player(1, 1, 0), maps);

        maps.mapManager().finishLoad(attacker);
        drain(attacker);
        killMonster(maps.combatService(), attacker);
        clock.advanceMillis(9_001L);
        maps.monsterManager().update();
        drain(attacker);

        assertTrue(maps.combatService().canTargetMonster(attacker, 101));
        assertTrue(maps.combatService().attackMonster(attacker, 101));
        List<Message> messages = drain(attacker);
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(messages));
        var reader = messages.getFirst().reader();
        assertEquals(101, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    private static void killMonster(CombatService combat, Session attacker,
                                     Session... observers) throws Exception {
        for (int hit = 0; hit < 29; hit++) {
            assertTrue(combat.attackMonster(attacker, 101));
            drain(attacker);
            for (Session observer : observers) {
                drain(observer);
            }
        }
        assertTrue(combat.attackMonster(attacker, 101));
    }
}
