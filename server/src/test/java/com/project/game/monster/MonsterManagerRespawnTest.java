package com.project.game.monster;

import com.project.game.network.Session;
import com.project.game.network.message.MessageName;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.mapsWithMonsters;
import static com.project.game.testsupport.GameplayTestSupport.player;
import static com.project.game.testsupport.GameplayTestSupport.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterManagerRespawnTest {
    @Test
    void lifecycleRespawnsOnlyAfterTheStrictDeadline() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session attacker = session(player(1, 1, 0), maps);
        maps.finishLoad(attacker);
        drain(attacker);

        kill(maps, attacker, 101);
        assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());

        clock.advanceMillis(9_000L);
        maps.monsterManager().update();
        assertTrue(commands(drain(attacker)).stream()
                .noneMatch(command -> command == MessageName.MONSTER_RESPAWN));

        clock.advanceMillis(1L);
        maps.monsterManager().update();
        assertEquals(List.of(MessageName.MONSTER_RESPAWN), commands(drain(attacker)).stream()
                .filter(command -> command == MessageName.MONSTER_RESPAWN)
                .toList());
        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void zonesKeepIndependentMonsterRespawnState() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session attacker = session(player(1, 1, 1), maps);
        maps.finishLoad(attacker);
        drain(attacker);

        kill(maps, attacker, 101);

        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(0L, maps.monsterSnapshots(1, 1).getFirst().hp());
    }

    @Test
    void respawnDoesNotRestoreOldHostility() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session attacker = session(player(1, 1, 0), maps);
        maps.finishLoad(attacker);
        drain(attacker);
        kill(maps, attacker, 101);

        clock.advanceMillis(9_001L);
        maps.monsterManager().update();
        drain(attacker);
        clock.advanceMillis(2_000L);
        maps.monsterManager().update();

        assertTrue(commands(drain(attacker)).stream()
                .noneMatch(command -> command == MessageName.MONSTER_ATTACK));
    }

    private static void kill(GameplayServices maps, Session attacker, int monsterId) throws Exception {
        for (int hit = 0; hit < 30; hit++) {
            assertTrue(maps.attackMonster(attacker, monsterId));
            drain(attacker);
        }
    }
}
