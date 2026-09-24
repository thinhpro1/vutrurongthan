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

class MapServiceTest {
    @Test
    void finishLoadExchangesPresenceOnlyWithExistingSameZoneMembers() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.mapService().finishLoad(first);
        assertEquals(List.of(), drain(first));

        maps.mapService().finishLoad(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(second)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(first)));
        assertEquals(2, maps.mapService().memberCount(0, 0));
    }

    @Test
    void differentZonesDoNotExchangePresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 1));

        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);

        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(second));
        assertEquals(1, maps.mapService().memberCount(0, 0));
        assertEquals(1, maps.mapService().memberCount(0, 1));
    }

    @Test
    void movementIsSentToOtherMembersWithoutMoverAck() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);
        drain(first);
        drain(second);

        assertTrue(maps.mapService().movePlayer(second, 1260, 640));
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
        maps.mapService().finishLoad(player);
        drain(player);

        assertTrue(maps.combatService().attackMonster(player, 101, 10));
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
        maps.mapService().finishLoad(player);
        drain(player);

        assertTrue(maps.combatService().attackMonster(player, 101, 10));
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
        maps.mapService().finishLoad(mover);
        maps.mapService().finishLoad(observer);
        drain(mover);
        drain(observer);
        Zone zone = maps.findZone(0, 0);
        PlayerProfile before = mover.player();

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
    void leaveNotifiesOtherMembersOnce() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);
        drain(first);
        drain(second);

        maps.mapService().leave(second);
        List<Message> removed = drain(first);
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(removed));
        var reader = removed.get(0).reader();
        assertEquals(2, reader.readInt());
        assertEquals(0, reader.remaining());
        assertEquals(1, maps.mapService().memberCount(0, 0));

        maps.mapService().leave(second);
        assertEquals(List.of(), drain(first));
    }

    @Test
    void repeatedFinishLoadDoesNotDuplicateMembershipOrPresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));

        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(first);

        assertEquals(1, maps.mapService().memberCount(0, 0));
        assertEquals(List.of(), drain(first));
    }

    @Test
    void finishLoadRejectsDifferentSessionWithSamePlayerId() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(7, 0, 0));
        Session conflicting = session(player(7, 0, 0));

        assertTrue(maps.mapService().finishLoad(first));
        drain(first);

        assertFalse(maps.mapService().finishLoad(conflicting));
        assertEquals(1, maps.mapService().memberCount(0, 0));
        Zone zone = zoneFor(maps, 0, 0);
        assertTrue(zone.contains(first));
        assertFalse(zone.contains(conflicting));
        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(conflicting));
    }

    @Test
    void closedMembersAreNotSentPackets() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));
        maps.mapService().finishLoad(first);
        maps.mapService().finishLoad(second);
        drain(first);
        drain(second);
        second.close();

        assertTrue(maps.mapService().movePlayer(first, 1260, 640));
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
        assertEquals(2, maps.mapService().memberCount(0, 0));
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
        assertEquals(1, maps.mapService().memberCount(0, 0));
    }

    @Test
    void leaveLastThenRejoinUsesRetainedZoneAndRemainsDiscoverable() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0));
        Session second = session(player(2, 0, 0));

        maps.mapService().finishLoad(first);
        maps.mapService().leave(first);
        assertEquals(0, maps.mapService().memberCount(0, 0));

        maps.mapService().finishLoad(second);
        assertEquals(1, maps.mapService().memberCount(0, 0));
        maps.mapService().finishLoad(first);

        List<Message> firstMessages = drain(first);
        List<Message> secondMessages = drain(second);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(firstMessages));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(secondMessages));
        var firstReader = firstMessages.get(0).reader();
        var secondReader = secondMessages.get(0).reader();
        assertEquals(2, firstReader.readInt());
        assertEquals(1, secondReader.readInt());
        assertEquals(2, maps.mapService().memberCount(0, 0));
    }

    @Test
    void disconnectCannotFinishWhileJoinPresenceEnqueueIsInProgress() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session leaving = session(player(1, 0, 0), maps);
        Session joining = session(player(2, 0, 0), maps);
        maps.mapService().finishLoad(leaving);
        drain(leaving);

        BlockingOfferQueue joiningQueue = new BlockingOfferQueue();
        replaceSendQueue(joining, joiningQueue);
        Thread join = Thread.ofVirtual().start(() -> maps.mapService().finishLoad(joining));
        assertTrue(joiningQueue.offerEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch disconnectFinished = new CountDownLatch(1);
        Thread disconnect = Thread.ofVirtual().start(() -> {
            leaving.close();
            disconnectFinished.countDown();
        });
        assertFalse(disconnectFinished.await(1, TimeUnit.SECONDS));

        joiningQueue.releaseOffer.countDown();
        join.join();
        disconnect.join();
        assertEquals(List.of(MessageName.ADD_PLAYER, MessageName.REMOVE_PLAYER),
                commands(drain(joining)));
    }

    @Test
    void deadPlayerCannotMove() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(player(1, 0, 0).withHp(0), maps);
        maps.mapService().finishLoad(dead);
        drain(dead);

        int xBefore = dead.player().x();
        int yBefore = dead.player().y();
        assertFalse(maps.mapService().movePlayer(dead, xBefore + 100, yBefore + 100));
        assertEquals(xBefore, dead.player().x());
        assertEquals(yBefore, dead.player().y());
        assertEquals(List.of(), drain(dead));
    }

    @Test
    void deadPlayerCannotUseNormalMapChange() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(player(1, 0, 0).withHp(0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.mapService().finishLoad(dead);
        maps.mapService().finishLoad(observer);
        drain(dead);
        drain(observer);

        PlayerProfile before = dead.player();
        assertTrue(maps.mapService().changeMap(dead, 0, 0, 1, 0, 975, 648).isEmpty());
        assertEquals(before, dead.player());
        assertEquals(2, maps.mapService().memberCount(0, 0));
        assertEquals(List.of(), drain(observer));
    }

    @Test
    void returnTownFromDeathRemovesSourcePresenceAndRevivesAtDefaultSpawn() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        Session dead = session(player(1, 1, 0).withHp(0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(dead);
        maps.mapService().finishLoad(observer);
        drain(dead);
        drain(observer);

        PlayerProfile revived = maps.mapService().returnTownFromDeath(dead).orElseThrow();

        assertEquals(0, revived.mapId());
        assertEquals(0, revived.zoneId());
        assertEquals(1250, revived.x());
        assertEquals(648, revived.y());
        assertEquals(revived.currentStats().maxHp(), revived.hp());
        assertEquals(revived.currentStats().maxMp(), revived.mp());
        assertEquals(revived, dead.player());
        assertEquals(1, maps.mapService().memberCount(1, 0));
        assertEquals(0, maps.mapService().memberCount(0, 0));

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
        PlayerProfile original = alive.player();

        assertTrue(maps.mapService().returnTownFromDeath(alive).isEmpty());
        assertEquals(original, alive.player());
    }

    @Test
    void duplicateReturnTownFromDeathIsHarmless() {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(player(1, 1, 0).withHp(0), maps);

        assertTrue(maps.mapService().returnTownFromDeath(dead).isPresent());
        PlayerProfile once = dead.player();
        assertTrue(maps.mapService().returnTownFromDeath(dead).isEmpty());
        assertEquals(once, dead.player());
    }

    @Test
    void returnTownFromDeathSerializesWithConcurrentJoin() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session dead = session(player(1, 1, 0).withHp(0), maps);
        Session observer = session(player(2, 1, 0), maps);
        Session joining = session(player(3, 1, 0), maps);
        maps.mapService().finishLoad(dead);
        maps.mapService().finishLoad(observer);
        drain(dead);
        drain(observer);

        BlockingOfferQueue observerQueue = new BlockingOfferQueue();
        replaceSendQueue(observer, observerQueue);
        Thread revive = Thread.ofVirtual().start(() -> maps.mapService().returnTownFromDeath(dead));
        assertTrue(observerQueue.offerEntered.await(5, TimeUnit.SECONDS));

        Thread join = Thread.ofVirtual().start(() -> maps.mapService().finishLoad(joining));
        awaitBlocked(join);

        observerQueue.releaseOffer.countDown();
        revive.join();
        join.join();

        assertEquals(2, maps.mapService().memberCount(1, 0));
        assertEquals(0, maps.mapService().memberCount(0, 0));
        assertEquals(List.of(MessageName.REMOVE_PLAYER, MessageName.ADD_PLAYER),
                commands(drain(observer)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(joining)));
    }

    @Test
    void finishLoadRejectsFullZoneWithoutBroadcast() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 2);
        Session first = session(player(1, 0, 0), maps);
        Session second = session(player(2, 0, 0), maps);

        assertTrue(maps.mapService().finishLoad(first));
        drain(first);

        assertFalse(maps.mapService().finishLoad(second));
        assertEquals(1, maps.mapService().memberCount(0, 0));
        assertEquals(0, second.queuedMessages());
    }

    @Test
    void changeMapRejectsFullDestinationBeforeRemovingSourceMember() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 2, 1);
        Session source = session(player(1, 0, 0), maps);
        Session blocker = session(player(2, 1, 0), maps);
        maps.mapService().finishLoad(source);
        maps.mapService().finishLoad(blocker);
        drain(source);
        drain(blocker);
        PlayerProfile before = source.player();

        assertTrue(maps.mapService().changeMap(
                source, 0, 0, 1, 0, 975, 648).isEmpty());

        assertEquals(before, source.player());
        assertEquals(1, maps.mapService().memberCount(0, 0));
        assertEquals(1, maps.mapService().memberCount(1, 0));
        assertEquals(List.of(), drain(source));
    }

    @Test
    void changeMapRejectsOfflineAndOutOfRangeDestinations() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "OFFLINE", 1, 1);
        Session source = session(player(1, 0, 0), maps);
        maps.mapService().finishLoad(source);
        PlayerProfile before = source.player();

        assertTrue(maps.mapService().changeMap(
                source, 0, 0, 1, 0, 975, 648).isEmpty());
        assertTrue(maps.mapService().changeMap(
                source, 0, 0, 0, 2, 975, 648).isEmpty());
        assertEquals(before, source.player());
        assertEquals(1, maps.mapService().memberCount(0, 0));
    }

    @Test
    void deathReturnRejectsFullTownWithoutRemovingSourceMember() throws Exception {
        GameplayServices maps = policyMaps("ONLINE", "ONLINE", 1, 1);
        Session dead = session(player(1, 1, 0).withHp(0), maps);
        Session blocker = session(player(2, 0, 0), maps);
        maps.mapService().finishLoad(dead);
        maps.mapService().finishLoad(blocker);
        PlayerProfile before = dead.player();

        assertTrue(maps.mapService().returnTownFromDeath(dead).isEmpty());

        assertEquals(before, dead.player());
        assertEquals(1, maps.mapService().memberCount(1, 0));
        assertEquals(1, maps.mapService().memberCount(0, 0));
    }

    private static GameplayServices policyMaps(
            String map0Type, String map1Type, int map0MaxPlayer, int map1MaxPlayer) {
        Map<Integer, MapTemplate> canonical = MapTestSupport.canonicalMaps();
        MapTemplate map0 = withPolicy(
                canonical.get(0), map0Type, 1, 2, map0MaxPlayer);
        MapTemplate map1 = withPolicy(
                canonical.get(1), map1Type, 1, 2, map1MaxPlayer);
        return new GameplayServices(
                Map.of(map0.id(), map0, map1.id(), map1), GameResources.unavailable());
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
            if (maps.mapService().finishLoad(session)) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
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
            moved.set(maps.mapService().movePlayer(session, x, y));
        } finally {
            finished.countDown();
        }
    }
}
