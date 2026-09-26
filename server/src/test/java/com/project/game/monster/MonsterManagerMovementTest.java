package com.project.game.monster;

import com.project.game.network.Session;
import com.project.game.network.message.MessageName;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static com.project.game.testsupport.GameplayTestSupport.at;
import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.mapsWithMonsters;
import static com.project.game.testsupport.GameplayTestSupport.player;
import static com.project.game.testsupport.GameplayTestSupport.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterManagerMovementTest {
    @Test
    void lifecyclePatrolIsServerAuthoritative() {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));

        maps.monsterManager().update(maps.mapManager());

        assertEquals(979, maps.monsterSnapshots(1, 0).getFirst().x());
    }

    @Test
    void nearbyUnhostilePlayerDoesNotRedirectIdlePatrol() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session player = session(at(player(1, 1, 0), 1000, 936), maps);
        maps.finishLoad(player);
        drain(player);

        maps.monsterManager().update(maps.mapManager());

        assertEquals(979, maps.monsterSnapshots(1, 0).getFirst().x());
        assertTrue(commands(drain(player)).contains(MessageName.MONSTER_MOVE));
    }

    @Test
    void hitMonsterChasesAHostilePlayerOnTheNextLifecycleTick() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session player = session(at(player(1, 1, 0), 1900, 936), maps);
        maps.finishLoad(player);
        drain(player);
        assertTrue(maps.attackMonster(player, 101));
        drain(player);

        maps.monsterManager().update(maps.mapManager());

        assertEquals(979, maps.monsterSnapshots(1, 0).getFirst().x());
    }

    @Test
    void nearestHostilePlayerWinsAndLowerIdBreaksEqualDistance() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session lowerId = session(at(player(7, 1, 0), 25, 936), maps);
        Session higherId = session(at(player(8, 1, 0), 1925, 936), maps);
        maps.finishLoad(lowerId);
        maps.finishLoad(higherId);
        drain(lowerId);
        drain(higherId);
        assertTrue(maps.attackMonster(lowerId, 101));
        assertTrue(maps.attackMonster(higherId, 101));
        drain(lowerId);
        drain(higherId);

        maps.monsterManager().update(maps.mapManager());

        assertEquals(971, maps.monsterSnapshots(1, 0).getFirst().x());
    }

    @Test
    void hostileMonsterStopsInsideAttackRangeButChasesAtExactNineHundred() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices inside = mapsWithMonsters(clock, new Random(1L));
        Session insidePlayer = session(at(player(1, 1, 0), 1874, 936), inside);
        inside.finishLoad(insidePlayer);
        drain(insidePlayer);
        assertTrue(inside.attackMonster(insidePlayer, 101));
        drain(insidePlayer);

        inside.monsterManager().update(inside.mapManager());
        assertEquals(975, inside.monsterSnapshots(1, 0).getFirst().x());

        GameplayServices exact = mapsWithMonsters(clock, new Random(1L));
        Session exactPlayer = session(at(player(1, 1, 0), 1875, 936), exact);
        exact.finishLoad(exactPlayer);
        drain(exactPlayer);
        assertTrue(exact.attackMonster(exactPlayer, 101));
        drain(exactPlayer);

        exact.monsterManager().update(exact.mapManager());
        assertEquals(979, exact.monsterSnapshots(1, 0).getFirst().x());
    }

    @Test
    void standingNearMonsterDoesNotAutoAggro() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Session player = session(player(1, 1, 0), maps);
        maps.finishLoad(player);
        drain(player);

        maps.monsterManager().update(maps.mapManager());

        assertEquals(100L, player.player().hp());
        assertEquals(List.of(), commands(drain(player)).stream()
                .filter(command -> command == MessageName.MONSTER_ATTACK)
                .toList());
    }
}
