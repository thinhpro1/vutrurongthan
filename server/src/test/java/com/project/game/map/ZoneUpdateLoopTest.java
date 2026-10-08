package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.network.message.MessageName;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.mapsWithMonsters;
import static com.project.game.testsupport.GameplayTestSupport.player;
import static com.project.game.testsupport.GameplayTestSupport.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneUpdateLoopTest {
    private static final int DEAD = 1;

    @Test
    void emptyZoneFreezesKeepsMonsterStateAndRespawnsWhenPlayerReturns() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        GameplayServices maps = mapsWithMonsters(clock, new Random(1L));
        Zone zone = maps.findZone(1, 0);
        Session attacker = session(player(1, 1, 0), maps);
        maps.finishLoad(attacker);
        drain(attacker);
        for (int hit = 0; hit < 30; hit++) {
            assertTrue(maps.attackMonster(attacker, 101));
            drain(attacker);
        }
        assertEquals(DEAD, maps.monsterSnapshots(1, 0).getFirst().status());
        maps.monsterManager().start(maps.mapManager());

        try {

            maps.leave(attacker);
            awaitState(zone, ZoneWriter.State.FROZEN);

            // Hạn hồi sinh trôi qua trong lúc Zone nghỉ: không có nhịp nào chạy, Monster giữ nguyên.
            clock.advanceMillis(9_001L);
            Thread.sleep(300);
            assertEquals(ZoneWriter.State.FROZEN, zone.runtimeState());
            assertEquals(DEAD, maps.monsterSnapshots(1, 0).getFirst().status());
            assertEquals(0L, maps.monsterSnapshots(1, 0).getFirst().hp());

            Session returning = session(player(1, 1, 0), maps);
            maps.finishLoad(returning);

            assertTrue(awaitRespawn(returning), "first update after waking must apply the due respawn");
            assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        } finally {
            maps.monsterManager().stop(maps.mapManager());
        }
    }

    private static void awaitState(Zone zone, ZoneWriter.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (zone.runtimeState() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, zone.runtimeState());
    }

    private static boolean awaitRespawn(Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (commands(drain(session)).contains(MessageName.MONSTER_RESPAWN)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
