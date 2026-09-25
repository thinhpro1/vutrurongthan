package com.project.game.map;

import com.project.game.combat.*;
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
import com.project.game.testsupport.MapTestSupport;
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

class MapManagerTest {
    @Test
    void finishLoadExchangesPresenceOnlyWithExistingSameZoneMembers() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.mapManager().finishLoad(first);
        assertEquals(List.of(), drain(first));

        maps.mapManager().finishLoad(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(second)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(first)));
        assertEquals(2, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void differentZonesDoNotExchangePresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 1));

        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);

        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(second));
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(1, maps.mapManager().memberCount(0, 1));
    }

    @Test
    void movementIsSentToOtherMembersWithoutMoverAck() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);
        drain(first);
        drain(second);

        assertTrue(maps.mapManager().movePlayer(second, 1260, 640));
        assertEquals(1260, second.player().x());
        assertEquals(640, second.player().y());

        List<Message> firstMessages = drain(first);
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(firstMessages));
        var reader = firstMessages.get(0).reader();
        assertEquals(2, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
        assertEquals(List.of(), drain(second));
    }

    @Test
    void movePlayerSerializesWithMonsterHpMutation() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        BlockingRandom random = new BlockingRandom();
        GameplayServices maps = mapsWithMonsters(clock, random);
        Session player = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        assertTrue(maps.combat().attackMonster(player, 101));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(player)));
        clock.advanceMillis(1L);

        Thread lifecycle = Thread.ofVirtual().start(maps.monsterManager()::update);
        assertTrue(random.entered.await(5, TimeUnit.SECONDS));

        AtomicBoolean moved = new AtomicBoolean();
        CountDownLatch movementStarted = new CountDownLatch(1);
        CountDownLatch movementFinished = new CountDownLatch(1);
        Thread movement = Thread.ofVirtual().start(() ->
                moveAndSignal(maps, player, 1260, 640, moved,
                        movementStarted, movementFinished));
        assertTrue(movementStarted.await(5, TimeUnit.SECONDS));
        assertFalse(movementFinished.await(100, TimeUnit.MILLISECONDS));

        random.release.countDown();
        lifecycle.join();
        movement.join();

        assertTrue(moved.get());
        assertEquals(90L, player.player().hp());
        assertEquals(1260, player.player().x());
        assertEquals(640, player.player().y());
        List<Integer> lifecycleCommands = commands(drain(player));
        assertEquals(MessageName.MONSTER_ATTACK, lifecycleCommands.getLast());
        assertEquals(5, lifecycleCommands.stream()
                .filter(command -> command == MessageName.MONSTER_MOVE)
                .count());
    }

    @Test
    void serializedLifecycleThenMovementChasesLatestPlayerPosition() throws Exception {
        MutableClock clock = new MutableClock(1_000_000L);
        BlockingRandom random = new BlockingRandom();
        GameplayServices maps = mapsWithMonsters(clock, random);
        Session player = session(player(1, 1, 0), maps);
        maps.mapManager().finishLoad(player);
        drain(player);

        assertTrue(maps.combat().attackMonster(player, 101));
        drain(player);
        clock.advanceMillis(1L);

        Thread lifecycle = Thread.ofVirtual().start(maps.monsterManager()::update);
        assertTrue(random.entered.await(5, TimeUnit.SECONDS));

        AtomicBoolean moved = new AtomicBoolean();
        CountDownLatch movementStarted = new CountDownLatch(1);
        CountDownLatch movementFinished = new CountDownLatch(1);
        Thread movement = Thread.ofVirtual().start(() ->
                moveAndSignal(maps, player, 2_100, 936, moved,
                        movementStarted, movementFinished));
        assertTrue(movementStarted.await(5, TimeUnit.SECONDS));
        assertFalse(movementFinished.await(100, TimeUnit.MILLISECONDS));

        random.release.countDown();
        lifecycle.join();
        movement.join();

        assertTrue(moved.get());
        assertEquals(2_100, player.player().x());
        assertEquals(936, player.player().y());
        withoutMonsterMoves(drain(player));

        clock.advanceMillis(1L);
        maps.monsterManager().update();
        List<Message> monsterMoves = drain(player).stream()
                .filter(message -> message.command() == MessageName.MONSTER_MOVE)
                .filter(message -> monsterMoveId(message) == 101)
                .toList();
        assertEquals(1, monsterMoves.size());
        assertMonsterMove(monsterMoves.getFirst(), 101, 979, 936, 1);
    }

    @Test
    void movementWaitsForPriorZoneActionAndBroadcastsAfterItRuns() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session mover = session(player(1, 0, 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(mover);
        maps.mapManager().finishLoad(observer);
        drain(mover);
        drain(observer);
        Zone zone = maps.findZone(0, 0);
        Player before = mover.player();

        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            priorStarted.countDown();
            try {
                if (!releasePrior.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("prior Zone action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        AtomicBoolean moved = new AtomicBoolean();
        CountDownLatch movementStarted = new CountDownLatch(1);
        CountDownLatch movementFinished = new CountDownLatch(1);
        Thread movement = Thread.ofVirtual().start(() ->
                moveAndSignal(maps, mover, 1260, 640, moved,
                        movementStarted, movementFinished));
        assertTrue(movementStarted.await(5, TimeUnit.SECONDS));
        assertFalse(movementFinished.await(100, TimeUnit.MILLISECONDS));
        assertEquals(before.x(), mover.player().x());
        assertEquals(before.y(), mover.player().y());

        releasePrior.countDown();
        assertTrue(movementFinished.await(5, TimeUnit.SECONDS));
        movement.join();

        assertTrue(moved.get());
        assertEquals(1260, mover.player().x());
        assertEquals(640, mover.player().y());
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(drain(observer)));
    }

    @Test
    void movementAdmissionBlocksConcurrentChangeMapUntilMovementPacketIsAdmitted()
            throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session mover = session(player(1, 0, 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(mover);
        maps.mapManager().finishLoad(observer);
        drain(mover);
        drain(observer);

        BlockingOfferQueue observerQueue = new BlockingOfferQueue();
        replaceSendQueue(observer, observerQueue);
        AtomicBoolean moved = new AtomicBoolean();
        Thread movement = Thread.ofVirtual().start(() ->
                moved.set(maps.mapManager().movePlayer(mover, 4464, 936)));
        assertTrue(observerQueue.offerEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean changed = new AtomicBoolean();
        CountDownLatch changeStarted = new CountDownLatch(1);
        CountDownLatch changeFinished = new CountDownLatch(1);
        Thread changeMap = Thread.ofVirtual().start(() -> {
            changeStarted.countDown();
            try {
                changed.set(maps.mapManager().changeMap(mover) != null);
            } finally {
                changeFinished.countDown();
            }
        });
        assertTrue(changeStarted.await(5, TimeUnit.SECONDS));

        assertFalse(changeFinished.await(100, TimeUnit.MILLISECONDS));
        assertEquals(0, mover.player().mapId());

        observerQueue.releaseOffer.countDown();
        movement.join(5_000);
        changeMap.join(5_000);

        assertFalse(movement.isAlive());
        assertFalse(changeMap.isAlive());
        assertTrue(moved.get());
        assertTrue(changed.get());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(0, maps.mapManager().memberCount(1, 0));
        assertEquals(1, mover.player().mapId());
        assertEquals(List.of(MessageName.PLAYER_MOVE, MessageName.REMOVE_PLAYER),
                commands(drain(observer)));
    }

    @Test
    void finishLoadWaitsBehindPriorZoneAction() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session joining = session(player(1, 0, 0), maps);
        Zone zone = maps.findZone(0, 0);
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        AtomicBoolean joined = new AtomicBoolean();
        CountDownLatch joinFinished = new CountDownLatch(1);
        Thread join = Thread.ofVirtual().start(() -> {
            try {
                joined.set(maps.mapManager().finishLoad(joining));
            } finally {
                joinFinished.countDown();
            }
        });

        assertFalse(joinFinished.await(100, TimeUnit.MILLISECONDS));
        assertEquals(0, maps.mapManager().memberCount(0, 0));
        releasePrior.countDown();
        assertTrue(joinFinished.await(5, TimeUnit.SECONDS));
        join.join();

        assertTrue(joined.get());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void finishLoadAdmissionBlocksConcurrentChangeMapUntilPresenceIsAdmitted()
            throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session source = session(at(player(1, 0, 0), 4464, 936), maps);
        Session joining = session(player(2, 0, 0), maps);
        assertTrue(maps.mapManager().finishLoad(source));
        drain(source);

        BlockingOfferQueue joiningQueue = new BlockingOfferQueue();
        replaceSendQueue(joining, joiningQueue);
        AtomicBoolean joined = new AtomicBoolean();
        CountDownLatch joinFinished = new CountDownLatch(1);
        Thread join = Thread.ofVirtual().start(() -> {
            try {
                joined.set(maps.mapManager().finishLoad(joining));
            } finally {
                joinFinished.countDown();
            }
        });
        assertTrue(joiningQueue.offerEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean changed = new AtomicBoolean();
        CountDownLatch changeFinished = new CountDownLatch(1);
        Thread changeMap = Thread.ofVirtual().start(() -> {
            try {
                changed.set(maps.mapManager().changeMap(source) != null);
            } finally {
                changeFinished.countDown();
            }
        });

        assertFalse(changeFinished.await(100, TimeUnit.MILLISECONDS));
        assertEquals(0, source.player().mapId());

        joiningQueue.releaseOffer.countDown();
        join.join(5_000);
        changeMap.join(5_000);

        assertFalse(join.isAlive());
        assertFalse(changeMap.isAlive());
        assertTrue(joined.get());
        assertTrue(changed.get());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(0, maps.mapManager().memberCount(1, 0));
        assertEquals(1, source.player().mapId());
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(source)));
        assertEquals(List.of(MessageName.ADD_PLAYER, MessageName.REMOVE_PLAYER),
                commands(drain(joining)));
    }

    @Test
    void leaveWaitsBehindPriorZoneAction() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session leaving = session(player(1, 0, 0), maps);
        assertTrue(maps.mapManager().finishLoad(leaving));
        drain(leaving);
        Zone zone = maps.findZone(0, 0);
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch leaveFinished = new CountDownLatch(1);
        Thread leave = Thread.ofVirtual().start(() -> {
            try {
                maps.mapManager().leave(leaving);
            } finally {
                leaveFinished.countDown();
            }
        });

        assertFalse(leaveFinished.await(100, TimeUnit.MILLISECONDS));
        assertTrue(zone.hasPlayer(leaving));
        releasePrior.countDown();
        assertTrue(leaveFinished.await(5, TimeUnit.SECONDS));
        leave.join();

        assertFalse(zone.hasPlayer(leaving));
        assertEquals(0, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void leaveNotifiesOtherMembersOnce() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);
        drain(first);
        drain(second);

        maps.mapManager().leave(second);
        List<Message> removed = drain(first);
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(removed));
        var reader = removed.get(0).reader();
        assertEquals(2, reader.readInt());
        assertEquals(0, reader.remaining());
        assertEquals(1, maps.mapManager().memberCount(0, 0));

        maps.mapManager().leave(second);
        assertEquals(List.of(), drain(first));
    }

    @Test
    void repeatedFinishLoadDoesNotDuplicateMembershipOrPresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));

        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(first);

        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(List.of(), drain(first));
    }

    @Test
    void finishLoadRejectsDifferentSessionWithSamePlayerId() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(7, 0, 0));
        Session conflicting = session(player(7, 0, 0));

        assertTrue(maps.mapManager().finishLoad(first));
        drain(first);

        assertFalse(maps.mapManager().finishLoad(conflicting));
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        Zone zone = zoneFor(maps, 0, 0);
        assertTrue(zone.hasPlayer(first));
        assertFalse(zone.hasPlayer(conflicting));
        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(conflicting));
    }

    @Test
    void closedMembersAreNotSentPackets() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapManager().finishLoad(first);
        maps.mapManager().finishLoad(second);
        drain(first);
        drain(second);
        second.close();
        drain(first);

        assertTrue(maps.mapManager().movePlayer(first, 1260, 640));
        assertEquals(List.of(), drain(first));
    }

    @Test
    void simultaneousSameZoneJoinsExchangeOnePresencePacketEach() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstJoin = Thread.ofVirtual().start(() -> joinAtBarrier(start, maps, first, failure));
        Thread secondJoin = Thread.ofVirtual().start(() -> joinAtBarrier(start, maps, second, failure));

        start.await();
        firstJoin.join();
        secondJoin.join();

        if (failure.get() != null) {
            throw new AssertionError("concurrent join failed", failure.get());
        }
        assertEquals(2, maps.mapManager().memberCount(0, 0));
        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(firstMessages));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(secondMessages));
        var firstReader = firstMessages.get(0).reader();
        var secondReader = secondMessages.get(0).reader();
        assertEquals(2, firstReader.readInt());
        assertEquals(1, secondReader.readInt());
    }

    @Test
    void simultaneousFinishLoadAdmissionEnforcesCapacityAtomically() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session first = session(player(1, 0, 0), maps);
        Session second = session(player(2, 0, 0), maps);
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread firstJoin = Thread.ofVirtual().start(() -> finishLoadAtBarrier(
                start, maps, first, successes, failures, failure));
        Thread secondJoin = Thread.ofVirtual().start(() -> finishLoadAtBarrier(
                start, maps, second, successes, failures, failure));

        start.await();
        firstJoin.join();
        secondJoin.join();

        if (failure.get() != null) {
            throw new AssertionError("concurrent finish-load failed", failure.get());
        }
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void leaveLastThenRejoinUsesRetainedZoneAndRemainsDiscoverable() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.mapManager().finishLoad(first);
        maps.mapManager().leave(first);
        assertEquals(0, maps.mapManager().memberCount(0, 0));

        maps.mapManager().finishLoad(second);
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        maps.mapManager().finishLoad(first);

        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(firstMessages));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(secondMessages));
        var firstReader = firstMessages.get(0).reader();
        var secondReader = secondMessages.get(0).reader();
        assertEquals(2, firstReader.readInt());
        assertEquals(1, secondReader.readInt());
        assertEquals(2, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void disconnectCannotFinishWhileJoinPresenceEnqueueIsInProgress() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session leaving = session(player(1, 0, 0), maps);
        Session joining = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(leaving);
        drain(leaving);

        BlockingOfferQueue joiningQueue = new BlockingOfferQueue();
        replaceSendQueue(joining, joiningQueue);
        Thread join = Thread.ofVirtual().start(() -> maps.mapManager().finishLoad(joining));
        assertTrue(joiningQueue.offerEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch disconnectFinished = new CountDownLatch(1);
        Thread disconnect = Thread.ofVirtual().start(() -> {
            leaving.close();
            disconnectFinished.countDown();
        });
        assertFalse(disconnectFinished.await(100, TimeUnit.MILLISECONDS));

        joiningQueue.releaseOffer.countDown();
        join.join();
        disconnect.join();
        assertEquals(List.of(MessageName.ADD_PLAYER, MessageName.REMOVE_PLAYER),
                commands(drain(joining)));
    }

    @Test
    void finishLoadDoesNotReaddClosedSessionAfterQueuedLeave() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session joining = session(player(1, 0, 0), maps);
        Zone zone = maps.findZone(0, 0);
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(zone.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch finishStarted = new CountDownLatch(1);
        AtomicBoolean joined = new AtomicBoolean();
        Thread finishLoad = Thread.ofVirtual().start(() -> {
            finishStarted.countDown();
            joined.set(maps.mapManager().finishLoad(joining));
        });
        assertTrue(finishStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch closeStarted = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            closeStarted.countDown();
            joining.close();
        });
        assertTrue(closeStarted.await(5, TimeUnit.SECONDS));
        awaitClosed(joining);

        releasePrior.countDown();
        finishLoad.join(1_000);
        close.join(1_000);

        assertFalse(finishLoad.isAlive());
        assertFalse(close.isAlive());
        assertFalse(joined.get());
        assertEquals(0, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void deadPlayerCannotMove() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(hp(player(1, 0, 0), 0), maps);
        maps.mapManager().finishLoad(dead);
        drain(dead);

        int xBefore = dead.player().x();
        int yBefore = dead.player().y();
        assertFalse(maps.mapManager().movePlayer(dead, xBefore + 100, yBefore + 100));
        assertEquals(xBefore, dead.player().x());
        assertEquals(yBefore, dead.player().y());
        assertEquals(List.of(), drain(dead));
    }

    @Test
    void closedNonMemberCannotMoveOrMutatePlayer() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session closed = session(player(1, 0, 0), maps);
        maps.mapManager().finishLoad(closed);
        drain(closed);
        closed.close();

        Player before = closed.player();
        assertFalse(maps.mapManager().movePlayer(closed, before.x() + 100, before.y() + 100));
        assertEquals(before, closed.player());
        assertEquals(0, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void deadPlayerCannotUseNormalMapChange() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(hp(player(1, 0, 0), 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(dead);
        maps.mapManager().finishLoad(observer);
        drain(dead);
        drain(observer);

        Player before = dead.player();
        assertNull(maps.mapManager().changeMap(dead));
        assertEquals(before, dead.player());
        assertEquals(2, maps.mapManager().memberCount(0, 0));
        assertEquals(List.of(), drain(observer));
    }

    @Test
    void returnTownWaitsForPriorLethalDamageInTheSourceZone() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(player(1, 1, 0), maps);
        assertTrue(maps.mapManager().finishLoad(dead));
        Zone source = maps.findZone(1, 0);
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(source.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
            dead.player().injure(dead.player().hp());
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<MapManager.MapChange> change = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        Thread returnTown = Thread.ofVirtual().start(() -> {
            try {
                change.set(maps.mapManager().returnTownFromDeath(dead));
            } finally {
                finished.countDown();
            }
        });

        assertFalse(finished.await(100, TimeUnit.MILLISECONDS));
        releasePrior.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        returnTown.join();

        assertNotNull(change.get());
        assertEquals(0, change.get().player().mapId());
        assertTrue(change.get().player().hp() > 0);
    }

    @Test
    void changeMapWaitsForPriorMovementBeforeFindingWaypoint() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session mover = session(player(1, 0, 0), maps);
        assertTrue(maps.mapManager().finishLoad(mover));
        Zone source = maps.findZone(0, 0);
        CountDownLatch priorStarted = new CountDownLatch(1);
        CountDownLatch releasePrior = new CountDownLatch(1);
        assertTrue(source.submit(() -> {
            priorStarted.countDown();
            awaitRelease(releasePrior);
            mover.player().move(4464, 936);
        }));
        assertTrue(priorStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<MapManager.MapChange> change = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        Thread changeMap = Thread.ofVirtual().start(() -> {
            try {
                change.set(maps.mapManager().changeMap(mover));
            } finally {
                finished.countDown();
            }
        });

        assertFalse(finished.await(100, TimeUnit.MILLISECONDS));
        releasePrior.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        changeMap.join();

        assertNotNull(change.get());
        assertEquals(1, change.get().player().mapId());
        assertEquals(0, change.get().zoneId());
    }

    @Test
    void returnTownFromDeathRemovesSourcePresenceAndRevivesAtDefaultSpawn() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(dead);
        maps.mapManager().finishLoad(observer);
        drain(dead);
        drain(observer);

        Player revived = dead.player();
        assertNotNull(maps.mapManager().returnTownFromDeath(dead));

        assertEquals(0, revived.mapId());
        assertEquals(0, revived.zoneId());
        assertEquals(1250, revived.x());
        assertEquals(648, revived.y());
        assertEquals(revived.currentStats().maxHp(), revived.hp());
        assertEquals(revived.currentStats().maxMp(), revived.mp());
        assertSame(revived, dead.player());
        assertEquals(1, maps.mapManager().memberCount(1, 0));
        assertEquals(0, maps.mapManager().memberCount(0, 0));

        List<Message> observerMessages = drain(observer);
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(observerMessages));
        var reader = observerMessages.getFirst().reader();
        assertEquals(dead.player().id(), reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void returnTownFromDeathIgnoresLivingPlayer() {
        GameplayServices maps = mapsWithoutMonsters();
        Session alive = session(player(1, 0, 0), maps);
        Player original = alive.player();

        assertNull(maps.mapManager().returnTownFromDeath(alive));
        assertEquals(original, alive.player());
    }

    @Test
    void sameZoneReturnTownCapturesStableStateWithoutRemovingPresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(hp(player(1, 0, 0), 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(dead);
        maps.mapManager().finishLoad(observer);
        drain(dead);
        drain(observer);

        MapManager.MapChange change = maps.mapManager().returnTownFromDeath(dead);
        assertNotNull(change);
        assertEquals(2, maps.mapManager().memberCount(0, 0));
        assertEquals(List.of(), drain(observer));

        PlayerSaveData stable = change.player();
        assertTrue(maps.mapManager().movePlayer(dead, 1260, 640));
        assertEquals(1260, dead.player().x());
        assertEquals(stable.x(), change.player().x());
        assertEquals(stable.y(), change.player().y());
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(drain(observer)));
    }

    @Test
    void duplicateReturnTownFromDeathIsHarmless() {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        maps.mapManager().finishLoad(dead);

        Player once = dead.player();
        assertNotNull(maps.mapManager().returnTownFromDeath(dead));
        assertNull(maps.mapManager().returnTownFromDeath(dead));
        assertSame(once, dead.player());
    }

    @Test
    void returnTownFromDeathSerializesWithConcurrentJoin() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        Session joining = session(player(3, 1, 0), maps);
        maps.mapManager().finishLoad(dead);
        maps.mapManager().finishLoad(observer);
        drain(dead);
        drain(observer);

        BlockingOfferQueue observerQueue = new BlockingOfferQueue();
        replaceSendQueue(observer, observerQueue);
        Thread revive = Thread.ofVirtual().start(() -> maps.mapManager().returnTownFromDeath(dead));
        assertTrue(observerQueue.offerEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch joinFinished = new CountDownLatch(1);
        AtomicBoolean joined = new AtomicBoolean();
        Thread join = Thread.ofVirtual().start(() -> {
            try {
                joined.set(maps.mapManager().finishLoad(joining));
            } finally {
                joinFinished.countDown();
            }
        });
        assertFalse(joinFinished.await(100, TimeUnit.MILLISECONDS));

        observerQueue.releaseOffer.countDown();
        revive.join();
        assertTrue(joinFinished.await(5, TimeUnit.SECONDS));
        join.join();

        assertTrue(joined.get());
        assertEquals(2, maps.mapManager().memberCount(1, 0));
        assertEquals(0, maps.mapManager().memberCount(0, 0));
        assertEquals(List.of(MessageName.REMOVE_PLAYER, MessageName.ADD_PLAYER),
                commands(drain(observer)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(joining)));
    }

    @Test
    void finishLoadRejectsFullZoneWithoutBroadcast() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 2);
        Session first = session(player(1, 0, 0), maps);
        Session second = session(player(2, 0, 0), maps);

        assertTrue(maps.mapManager().finishLoad(first));
        drain(first);

        assertFalse(maps.mapManager().finishLoad(second));
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(0, second.queuedMessages());
    }

    @Test
    void changeMapRejectsFullDestinationBeforeRemovingSourceMember() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 1);
        Player sourcePlayer = player(1, 0, 0);
        sourcePlayer.changeMap(0, 0, 4464, 936);
        Session source = session(sourcePlayer, maps);
        Session blocker = session(player(2, 1, 0), maps);
        maps.mapManager().finishLoad(source);
        maps.mapManager().finishLoad(blocker);
        drain(source);
        drain(blocker);
        Player before = source.player();

        assertNull(maps.mapManager().changeMap(source));

        assertEquals(before, source.player());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
        assertEquals(1, maps.mapManager().memberCount(1, 0));
        assertEquals(List.of(), drain(source));
    }

    @Test
    void changeMapRejectsOfflineAndOutOfRangeDestinations() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "OFFLINE", 1, 1);
        Player sourcePlayer = player(1, 0, 0);
        sourcePlayer.changeMap(0, 0, 4464, 936);
        Session source = session(sourcePlayer, maps);
        maps.mapManager().finishLoad(source);
        Player before = source.player();

        assertNull(maps.mapManager().changeMap(source));
        assertNull(maps.mapManager().changeMap(source));
        assertEquals(before, source.player());
        assertEquals(1, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void deathReturnRejectsFullTownWithoutRemovingSourceMember() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        Session blocker = session(player(2, 0, 0), maps);
        maps.mapManager().finishLoad(dead);
        maps.mapManager().finishLoad(blocker);
        Player before = dead.player();

        assertNull(maps.mapManager().returnTownFromDeath(dead));

        assertEquals(before, dead.player());
        assertEquals(1, maps.mapManager().memberCount(1, 0));
        assertEquals(1, maps.mapManager().memberCount(0, 0));
    }

    @Test
    void deathReturnReservesTownBeforeDetachingSource() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 1);
        Session firstDead = session(hp(player(1, 1, 0), 0), maps);
        Session secondDead = session(hp(player(2, 1, 1), 0), maps);
        Session townPlayer = session(player(3, 0, 0), maps);
        assertTrue(maps.mapManager().finishLoad(firstDead));
        assertTrue(maps.mapManager().finishLoad(secondDead));
        assertTrue(maps.mapManager().finishLoad(townPlayer));
        drain(firstDead);
        drain(secondDead);
        drain(townPlayer);

        assertNotNull(maps.mapManager().returnTownFromDeath(firstDead));
        assertNull(firstDead.zone());
        assertEquals(1, maps.findZone(0, 0).reservedCount());
        assertEquals(1, maps.mapManager().memberCount(0, 0));

        assertNull(maps.mapManager().returnTownFromDeath(secondDead));
        assertSame(maps.findZone(1, 1), secondDead.zone());
        assertTrue(secondDead.player().isDead());
        assertEquals(1, maps.findZone(0, 0).reservedCount());
    }

    @Test
    void changeMapReservesDestinationBeforeDetachingSource() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session source = session(at(player(1, 0, 0), 4464, 936), maps);
        Session blocked = session(at(player(2, 0, 1), 4464, 936), maps);

        assertTrue(maps.mapManager().finishLoad(source));
        assertTrue(maps.mapManager().finishLoad(blocked));
        drain(source);
        drain(blocked);

        MapManager.MapChange change = maps.mapManager().changeMap(source);

        assertNotNull(change);
        assertNull(source.zone());
        assertEquals(1, maps.findZone(1, 0).reservedCount());
        assertEquals(0, maps.mapManager().memberCount(1, 0));
        assertNull(maps.mapManager().changeMap(blocked));
        assertSame(maps.findZone(0, 1), blocked.zone());
        assertEquals(0, blocked.player().mapId());
        assertEquals(1, blocked.player().zoneId());
        assertEquals(1, maps.findZone(1, 0).reservedCount());
    }

    @Test
    void finishLoadConsumesDestinationReservationOnce() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 2);
        Session observer = session(player(1, 1, 0), maps);
        Session moving = session(at(player(2, 0, 0), 4464, 936), maps);

        assertTrue(maps.mapManager().finishLoad(observer));
        drain(observer);
        assertTrue(maps.mapManager().finishLoad(moving));
        drain(moving);

        assertNotNull(maps.mapManager().changeMap(moving));
        assertEquals(1, maps.findZone(1, 0).reservedCount());
        assertTrue(maps.mapManager().finishLoad(moving));
        assertEquals(0, maps.findZone(1, 0).reservedCount());
        assertEquals(2, maps.mapManager().memberCount(1, 0));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(observer)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(moving)));

        assertTrue(maps.mapManager().finishLoad(moving));
        assertEquals(List.of(), drain(observer));
        assertEquals(List.of(), drain(moving));
    }

    @Test
    void competingSourceZonesAllowOnlyOneDestinationReservation() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session first = session(at(player(1, 0, 0), 4464, 936), maps);
        Session second = session(at(player(2, 0, 1), 4464, 936), maps);
        assertTrue(maps.mapManager().finishLoad(first));
        assertTrue(maps.mapManager().finishLoad(second));
        drain(first);
        drain(second);

        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<MapManager.MapChange> firstChange = new AtomicReference<>();
        AtomicReference<MapManager.MapChange> secondChange = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstThread = Thread.ofVirtual().start(() -> changeAtBarrier(
                start, maps, first, firstChange, failure));
        Thread secondThread = Thread.ofVirtual().start(() -> changeAtBarrier(
                start, maps, second, secondChange, failure));
        start.await();
        firstThread.join(5_000);
        secondThread.join(5_000);

        if (failure.get() != null) {
            throw new AssertionError("concurrent map change failed", failure.get());
        }
        assertFalse(firstThread.isAlive());
        assertFalse(secondThread.isAlive());
        assertEquals(1, (firstChange.get() == null ? 0 : 1)
                + (secondChange.get() == null ? 0 : 1));
        Session loser = firstChange.get() == null ? first : second;
        assertEquals(0, loser.player().mapId());
        assertEquals(loser == first ? 0 : 1, loser.player().zoneId());
        assertTrue(loser.zone().hasPlayer(loser));
        assertEquals(0, maps.mapManager().memberCount(1, 0));
        assertEquals(1, maps.findZone(1, 0).reservedCount());
    }

    @Test
    void disconnectBeforeFinishLoadReleasesDestinationReservation() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session disconnected = session(at(player(1, 0, 0), 4464, 936), maps);
        assertTrue(maps.mapManager().finishLoad(disconnected));
        drain(disconnected);
        assertNotNull(maps.mapManager().changeMap(disconnected));
        Zone destination = maps.findZone(1, 0);
        assertEquals(1, destination.reservedCount());

        disconnected.close();

        assertEquals(SessionState.CLOSED, disconnected.state());
        assertEquals(0, destination.reservedCount());
        assertEquals(0, destination.size());
        PlayerSaveData saved = maps.mapManager().leave(disconnected);
        assertEquals(1, saved.mapId());
    }

    @Test
    void sourceRevalidationFailureRollsBackDestinationReservation() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 1);
        Session source = session(at(player(1, 0, 0), 4464, 936), maps);
        assertTrue(maps.mapManager().finishLoad(source));
        drain(source);
        Zone sourceZone = maps.findZone(0, 0);
        Zone destination = maps.findZone(1, 0);
        CountDownLatch destinationStarted = new CountDownLatch(1);
        CountDownLatch releaseDestination = new CountDownLatch(1);
        assertTrue(destination.submit(() -> {
            destinationStarted.countDown();
            awaitRelease(releaseDestination);
        }));
        assertTrue(destinationStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<MapManager.MapChange> change = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread transition = Thread.ofVirtual().start(() -> {
            try {
                change.set(maps.mapManager().changeMap(source));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });
        try {
            awaitWaiting(transition);
            sourceZone.call(() -> {
                source.player().move(1250, 648);
                return null;
            });
            releaseDestination.countDown();
            transition.join(5_000);
        } finally {
            releaseDestination.countDown();
            transition.join(5_000);
        }

        assertFalse(transition.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("source revalidation failed", failure.get());
        }
        assertNull(change.get());
        assertTrue(sourceZone.hasPlayer(source));
        assertSame(sourceZone, source.zone());
        assertEquals(0, source.player().mapId());
        assertEquals(0, source.player().zoneId());
        assertEquals(1250, source.player().x());
        assertEquals(648, source.player().y());
        assertEquals(0, destination.reservedCount());

        Session next = session(at(player(2, 0, 0), 4464, 936), maps);
        assertTrue(maps.mapManager().finishLoad(next));
        assertNotNull(maps.mapManager().changeMap(next));
        assertTrue(maps.mapManager().finishLoad(next));
        assertEquals(1, maps.mapManager().memberCount(1, 0));
    }

    @Test
    void postCommitChangeFailureKeepsDestinationReservation() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 1);
        Session source = session(at(player(1, 0, 0), 4464, 936), maps);
        Session observer = session(player(2, 0, 0), maps);
        assertTrue(maps.mapManager().finishLoad(source));
        assertTrue(maps.mapManager().finishLoad(observer));
        drain(source);
        drain(observer);
        replaceSendQueue(observer, new ThrowingOfferQueue());

        assertThrows(IllegalStateException.class, () -> maps.mapManager().changeMap(source));

        Zone destination = maps.findZone(1, 0);
        assertNull(source.zone());
        assertEquals(1, source.player().mapId());
        assertEquals(0, source.player().zoneId());
        assertEquals(1, destination.reservedCount());
        assertTrue(maps.mapManager().finishLoad(source));
        assertEquals(1, maps.mapManager().memberCount(1, 0));
    }

    @Test
    void postCommitDeathReturnFailureKeepsTownReservation() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 2);
        Session dead = session(hp(player(1, 1, 0), 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        assertTrue(maps.mapManager().finishLoad(dead));
        assertTrue(maps.mapManager().finishLoad(observer));
        drain(dead);
        drain(observer);
        replaceSendQueue(observer, new ThrowingOfferQueue());

        assertThrows(IllegalStateException.class,
                () -> maps.mapManager().returnTownFromDeath(dead));

        Zone town = maps.findZone(0, 0);
        assertNull(dead.zone());
        assertEquals(0, dead.player().mapId());
        assertEquals(0, dead.player().zoneId());
        assertFalse(dead.player().isDead());
        assertEquals(1, town.reservedCount());
        assertTrue(maps.mapManager().finishLoad(dead));
        assertEquals(1, maps.mapManager().memberCount(0, 0));
    }

    private static GameplayServices policyMaps(
            String map0Type, String map1Type, int map0MaxPlayer, int map1MaxPlayer) {
        java.util.Map<Integer, MapTemplate> canonical = MapTestSupport.canonicalMaps();
        MapTemplate map0 = withPolicy(
                canonical.get(0), map0Type, 1, 2, map0MaxPlayer);
        MapTemplate map1 = withPolicy(
                canonical.get(1), map1Type, 1, 2, map1MaxPlayer);
        return new GameplayServices(
                java.util.Map.of(map0.id(), map0, map1.id(), map1), GameResources.unavailable());
    }

    private static MapTemplate withPolicy(
            MapTemplate map, String type, int minZone, int maxZone, int maxPlayer) {
        return new MapTemplate(
                map.id(), map.name(), type, map.planet(), minZone, maxZone, maxPlayer,
                map.dataId(), map.data(), map.waypoints());
    }

    private static void finishLoadAtBarrier(
            CyclicBarrier start,
            GameplayServices maps,
            Session session,
            AtomicInteger successes,
            AtomicInteger failures,
            AtomicReference<Throwable> failure) {
        try {
            start.await();
            if (maps.mapManager().finishLoad(session)) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static void changeAtBarrier(
            CyclicBarrier start,
            GameplayServices maps,
            Session session,
            AtomicReference<MapManager.MapChange> change,
            AtomicReference<Throwable> failure) {
        try {
            start.await();
            change.set(maps.mapManager().changeMap(session));
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static void moveAndSignal(
            GameplayServices maps,
            Session session,
            int x,
            int y,
            AtomicBoolean moved,
            CountDownLatch started,
            CountDownLatch finished) {
        started.countDown();
        try {
            moved.set(maps.mapManager().movePlayer(session, x, y));
        } finally {
            finished.countDown();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("prior Zone action was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TERMINATED
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.WAITING, thread.getState(),
                "changeMap did not reach cross-Zone coordination");
    }

    private static final class ThrowingOfferQueue extends LinkedBlockingQueue<Message> {
        @Override
        public boolean offer(Message message) {
            throw new IllegalStateException("injected packet enqueue failure");
        }
    }

    private static void awaitClosed(Session session) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (session.state() != SessionState.CLOSED && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(SessionState.CLOSED, session.state());
    }
}
