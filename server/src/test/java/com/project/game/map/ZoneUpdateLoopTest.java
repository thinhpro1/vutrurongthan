package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;
import com.project.game.testsupport.GameplayTestSupport;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MutableClock;
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
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

    @Test
    void sessionKickedEarlierInAFailingUpdateIsStillClosed() throws Exception {
        long now = 1_000_000L;
        Zone zone = new Zone(1, 0, Integer.MAX_VALUE,
                new MonsterManager(GameResources.fromFrameRoot(
                        Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                        MonsterTestSupport.canonicalRepository())).createForMap(1),
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
        Session observer = GameplayTestSupport.session(playerAt(1, 975, 936));
        Session fighter = GameplayTestSupport.session(playerAt(2, 1348, 936));
        assertTrue(zone.enter(observer.player()));
        assertTrue(zone.enter(fighter.player()));

        // Monster 101 chết và đến hạn hồi sinh; Monster 102 thù fighter nên sẽ chọn mục tiêu.
        for (int hit = 0; hit < 30; hit++) {
            assertTrue(ZoneTestHooks.attackMonster(zone, observer, 101, now));
            drain(observer);
            drain(fighter);
        }
        assertTrue(ZoneTestHooks.attackMonster(zone, fighter, 102, now));
        drain(observer);
        drain(fighter);

        // Observer đầy hàng đợi: gói hồi sinh của 101 thất bại → observer bị kick.
        ArrayBlockingQueue<Message> full = new ArrayBlockingQueue<>(1);
        assertTrue(full.offer(new Message(MessageName.DIALOG_OK)));
        GameplayTestSupport.replaceSendQueue(observer, full);

        // Cùng nhịp đó, 102 chọn mục tiêu và random ném lỗi.
        CountDownLatch failed = new CountDownLatch(1);
        Random failing = new Random() {
            @Override
            public int nextInt(int bound) {
                failed.countDown();
                throw new IllegalStateException("synthetic update failure");
            }
        };
        // Mỗi nhịp tiến 10 s để 102 luôn đến lượt đánh: nhịp nào cũng lỗi, không nhịp nào "cứu" Session.
        zone.startUpdate(new SteppingClock(now + 60_000L, 10_000L), failing);
        try {
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            assertTrue(awaitClosed(observer), "Session kicked before the failure must still be closed");

            // Writer vẫn sống: input sau nhịp lỗi vẫn được chạy.
            assertTrue(ZoneTestHooks.move(zone, fighter, 1350, 936));
        } finally {
            zone.stopUpdate();
        }
    }

    private static final class SteppingClock extends Clock {
        private final java.util.concurrent.atomic.AtomicLong millis;
        private final long step;

        private SteppingClock(long start, long step) {
            this.millis = new java.util.concurrent.atomic.AtomicLong(start);
            this.step = step;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            return millis.getAndAdd(step);
        }
    }

    private static Player playerAt(int id, int x, int y) {
        return TestPlayers.at(TestPlayers.initial((long) id, id, "player" + id, 1), 1, 0, x, y);
    }

    private static boolean awaitClosed(Session session) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (session.state() != SessionState.CLOSED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return session.state() == SessionState.CLOSED;
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
