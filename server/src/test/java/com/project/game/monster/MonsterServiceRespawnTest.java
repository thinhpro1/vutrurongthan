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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.project.game.testsupport.GameplayTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MonsterServiceRespawnTest {

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
