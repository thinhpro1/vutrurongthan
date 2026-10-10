package com.project.game.map;

import com.project.game.testsupport.MonsterSnapshot;
import com.project.game.monster.Monster;
import com.project.game.monster.MonsterManager;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import com.project.game.testsupport.TestMaps;
import com.project.game.testsupport.TestPlayers;

import com.project.game.testsupport.TestServices;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.service.AreaService;
import com.project.game.network.SessionServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTest {
    @Test
    void playerEntersMovesAndLeaves() {
        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Session session = session(TestPlayers.at(
                TestPlayers.initial(1L, 7, "alpha1", 1), 1, 0, 100, 100));
        Player player = session.player();

        assertTrue(ZoneTestHooks.join(zone, player));
        assertSame(zone, session.zone());
        assertTrue(zone.hasPlayer(player));

        assertTrue(ZoneTestHooks.move(zone, session, 1260, 640));
        assertEquals(1260, player.x());
        assertEquals(640, player.y());

        zone.call(() -> {
            zone.leave(player);
            return null;
        });
        assertFalse(zone.hasPlayer(player));
        assertNull(session.zone());
        assertEquals(0, zone.playerCount());
    }

    @Test
    void playerStillLoadingTheMapCannotAct() {
        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Session session = session(TestPlayers.at(
                TestPlayers.initial(1L, 7, "alpha1", 1), 1, 0, 100, 100));
        Player player = session.player();
        zone.call(() -> {
            zone.enter(player);
            return null;
        });

        assertTrue(player.isLoading());
        assertFalse(ZoneTestHooks.move(zone, session, 1260, 640));

        zone.finishLoadMap(player);
        ZoneTestHooks.drain(zone);

        assertFalse(player.isLoading());
        assertTrue(ZoneTestHooks.move(zone, session, 1260, 640));
    }

    @Test
    void postIgnoresPlayersThatAreNotInTheZone() {
        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Player member = TestPlayers.at(TestPlayers.initial(1L, 7, "alpha1", 1), 1, 0, 100, 100);
        Player samePlayerId = TestPlayers.at(TestPlayers.initial(2L, 7, "alpha2", 1), 1, 0, 100, 100);
        session(member);
        session(samePlayerId);
        assertTrue(ZoneTestHooks.join(zone, member));
        AtomicBoolean ran = new AtomicBoolean();

        assertTrue(zone.post(samePlayerId, () -> ran.set(true)));
        ZoneTestHooks.drain(zone);

        assertFalse(ran.get(), "a different Player object with the same id is not a member");
        assertFalse(zone.hasPlayer(samePlayerId));
        assertEquals(1, zone.playerCount());
    }

    @Test
    void closedSessionDoesNotEnter() {
        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Session session = session(TestPlayers.at(
                TestPlayers.initial(1L, 7, "alpha1", 1), 1, 0, 100, 100));
        session.close();

        assertFalse(ZoneTestHooks.join(zone, session.player()));
        assertEquals(0, zone.playerCount());
    }

    @Test
    void blockingZoneCallsAreRejectedFromInsideAZoneThread() {
        Zone zone = zone(1, 0, Integer.MAX_VALUE, List.of());
        Player player = TestPlayers.at(TestPlayers.initial(1L, 7, "alpha1", 1), 1, 0, 100, 100);

        zone.call(() -> {
            assertThrows(IllegalStateException.class, () -> zone.tick(0L, new java.util.Random(1L)));
            assertThrows(IllegalStateException.class, () -> zone.logout(player));
            assertThrows(IllegalStateException.class, zone::stopUpdate);
            return null;
        });
    }

    @Test
    void requiresExplicitMonsterSeed() {
        assertThrows(
                NoSuchMethodException.class,
                () -> Zone.class.getConstructor(int.class, int.class));
    }

    @Test
    void runtimeStartsFrozenWithoutCreatingAnActiveWorker() {
        Zone zone = zone(1, 0, 10, List.of());

        assertEquals(ZoneWriter.State.FROZEN, zone.runtimeState());
    }

    @Test
    void lifecycleControlsStayPackagePrivateExceptSubmitBoundary() throws Exception {
        assertFalse(Modifier.isPublic(ZoneWriter.State.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("runtimeState").getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("stopRuntime").getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("call", Supplier.class).getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("submit", Runnable.class).getModifiers()));
    }

    @Test
    void runtimeRejectsNullActionsAndInvalidInputCapacity() {
        Zone zone = zone(1, 0, 10, List.of());

        assertThrows(NullPointerException.class, () -> zone.submit(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> zone(1, 0, 10, List.of(), 0));
    }

    @Test
    void queuedActionsRunInFifoOrderOnOneVirtualWriter() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Thread> writer = new AtomicReference<>();
        AtomicBoolean allVirtual = new AtomicBoolean(true);
        AtomicBoolean oneWriter = new AtomicBoolean(true);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(3);

        assertTrue(zone.submit(() -> {
            Thread current = Thread.currentThread();
            writer.set(current);
            allVirtual.set(current.isVirtual());
            order.add(1);
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                finished.countDown();
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertEquals(ZoneWriter.State.ACTIVE, zone.runtimeState());

        assertTrue(zone.submit(() -> {
            Thread current = Thread.currentThread();
            allVirtual.set(allVirtual.get() && current.isVirtual());
            oneWriter.set(oneWriter.get() && writer.get() == current);
            order.add(2);
            finished.countDown();
        }));
        assertTrue(zone.submit(() -> {
            Thread current = Thread.currentThread();
            allVirtual.set(allVirtual.get() && current.isVirtual());
            oneWriter.set(oneWriter.get() && writer.get() == current);
            order.add(3);
            finished.countDown();
        }));

        releaseFirst.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);

        assertEquals(List.of(1, 2, 3), order);
        assertTrue(allVirtual.get());
        assertTrue(oneWriter.get());
    }

    @Test
    void simultaneousSubmissionsShareOneVirtualWriter() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch submitted = new CountDownLatch(2);
        CountDownLatch finished = new CountDownLatch(3);
        AtomicReference<Thread> writer = new AtomicReference<>();
        AtomicBoolean oneWriter = new AtomicBoolean(true);
        CyclicBarrier barrier = new CyclicBarrier(3);

        assertTrue(zone.submit(() -> {
            writer.set(Thread.currentThread());
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                finished.countDown();
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread submitterOne = Thread.ofVirtual().start(() -> submitFromBarrier(
                zone, barrier, submitted, finished, writer, oneWriter));
        Thread submitterTwo = Thread.ofVirtual().start(() -> submitFromBarrier(
                zone, barrier, submitted, finished, writer, oneWriter));
        barrier.await();

        assertTrue(submitted.await(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        submitterOne.join();
        submitterTwo.join();
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);

        assertTrue(oneWriter.get());
    }

    @Test
    void runtimeReturnsToFrozenAfterQueueDrains() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch completed = new CountDownLatch(1);

        assertTrue(zone.submit(completed::countDown));
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
    }

    @Test
    void synchronousCallWaitsForQueuedActionAndReturnsResult() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                result.set(zone.call(() -> 42));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                callFinished.countDown();
            }
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();
        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        caller.join();

        if (failure.get() != null) {
            throw new AssertionError("Zone call failed", failure.get());
        }
        assertEquals(42, result.get());
    }

    @Test
    void synchronousCallExecutesInlineOnZoneWorker() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            result.set(zone.call(() -> 42));
            finished.countDown();
        }));

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
        assertEquals(42, result.get());
    }

    @Test
    void acceptedCallRestoresCallerInterruptAfterCompletion() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicBoolean interruptedAfterCall = new AtomicBoolean();
        AtomicReference<Integer> result = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread caller = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
            result.set(zone.call(() -> 42));
            interruptedAfterCall.set(Thread.currentThread().isInterrupted());
            callFinished.countDown();
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();
        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        caller.join();

        assertEquals(42, result.get());
        assertTrue(interruptedAfterCall.get());
    }

    @Test
    void requiredCallWaitsForTemporaryQueueFull() throws Exception {
        Zone zone = zone(1, 0, 10, List.of(), 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            awaitRelease(releaseFirst);
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(secondRan::countDown));

        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                result.set(zone.call(() -> 42));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                callFinished.countDown();
            }
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();

        assertTrue(secondRan.await(5, TimeUnit.SECONDS));
        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        caller.join();
        assertNull(failure.get());
        assertEquals(42, result.get());
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
    }

    @Test
    void requiredCallCompletesAfterInterruptAndRestoresInterruptStatus() throws Exception {
        Zone zone = zone(1, 0, 10, List.of(), 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            awaitRelease(releaseFirst);
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(() -> {
        }));

        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                result.set(zone.call(() -> 7));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
                callFinished.countDown();
            }
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        caller.interrupt();
        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();

        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        caller.join();
        assertNull(failure.get());
        assertEquals(7, result.get());
        assertTrue(interrupted.get());
    }

    @Test
    void requiredCallWaitingForCapacityWakesWhenRuntimeStops() throws Exception {
        Zone zone = zone(1, 0, 10, List.of(), 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            awaitRelease(releaseFirst);
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(() -> {
        }));

        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                zone.call(() -> 42);
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                callFinished.countDown();
            }
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        zone.stopRuntime();
        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        caller.join();

        assertTrue(failure.get() instanceof RejectedExecutionException);
        awaitRuntimeState(zone, ZoneWriter.State.STOPPED);
    }

    @Test
    void tryCallRejectsOverloadWithoutRunningLater() throws Exception {
        Zone zone = zone(1, 0, 10, List.of(), 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);
        AtomicBoolean rejectedActionRan = new AtomicBoolean();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            awaitRelease(releaseFirst);
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(secondRan::countDown));

        assertThrows(RejectedExecutionException.class,
                () -> zone.tryCall(() -> {
                    rejectedActionRan.set(true);
                    return 42;
                }));

        releaseFirst.countDown();
        assertTrue(secondRan.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
        assertFalse(rejectedActionRan.get());
    }

    @Test
    void stoppedRuntimeRejectsSynchronousCall() {
        Zone zone = zone(1, 0, 10, List.of());
        zone.stopRuntime();

        assertThrows(RejectedExecutionException.class, () -> zone.call(() -> 42));
    }

    @Test
    void stoppedRuntimeRejectsAcceptedCallInsteadOfLeavingCallerWaiting() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch callFinished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                zone.call(() -> 42);
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                callFinished.countDown();
            }
        });

        assertFalse(callFinished.await(100, TimeUnit.MILLISECONDS));
        zone.stopRuntime();
        assertTrue(callFinished.await(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        caller.join();

        assertTrue(failure.get() instanceof RejectedExecutionException);
        awaitRuntimeState(zone, ZoneWriter.State.STOPPED);
    }

    @Test
    void freezeWakeKeepsExistingMonsterRuntimeState() throws Exception {
        Monster monster = monsterForTest();
        Zone zone = zone(1, 0, 10, List.of(monster));
        CountDownLatch damaged = new CountDownLatch(1);
        CountDownLatch woke = new CountDownLatch(1);

        assertTrue(zone.submit(() -> {
            monster.injure(TestPlayers.initial(1L, 1, "alpha1", 0), 10, 0);
            damaged.countDown();
        }));
        assertTrue(damaged.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
        assertEquals(290, MonsterSnapshot.of(monster).hp());

        assertTrue(zone.submit(woke::countDown));
        assertTrue(woke.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
        assertEquals(290, MonsterSnapshot.of(monster).hp());
    }

    @Test
    void boundedRuntimeQueueRejectsOverflowWithoutBlocking() throws Exception {
        Zone zone = zone(1, 0, 10, List.of(), 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(secondRan::countDown));

        long startedAt = System.nanoTime();
        assertFalse(zone.submit(() -> {
            throw new AssertionError("overflow action must not run");
        }));
        long elapsed = System.nanoTime() - startedAt;

        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1));
        releaseFirst.countDown();
        assertTrue(secondRan.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
    }

    @Test
    void runtimeTaskFailureDoesNotStrandLifecycle() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch continued = new CountDownLatch(1);

        assertTrue(zone.submit(() -> {
            throw new IllegalStateException("expected test failure");
        }));
        assertTrue(zone.submit(continued::countDown));

        assertTrue(continued.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, ZoneWriter.State.FROZEN);
    }

    @Test
    void stoppedRuntimeRejectsNewActionsAndDiscardsPendingActions() throws Exception {
        Zone zone = zone(1, 0, 10, List.of());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean pendingRan = new AtomicBoolean();

        assertTrue(zone.submit(() -> {
            firstStarted.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first action was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(zone.submit(() -> pendingRan.set(true)));

        zone.stopRuntime();
        assertEquals(ZoneWriter.State.STOPPED, zone.runtimeState());
        assertFalse(zone.submit(() -> {
            throw new AssertionError("stopped action must not run");
        }));

        releaseFirst.countDown();
        awaitRuntimeState(zone, ZoneWriter.State.STOPPED);
        assertFalse(pendingRan.get());
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("action was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Zone zone(int mapId, int zoneId, int maxPlayer, List<Monster> monsters) {
        return new Zone(TestMaps.emptyMap(mapId, maxPlayer), zoneId, monsters, area());
    }

    private static Zone zone(
            int mapId,
            int zoneId,
            int maxPlayer,
            List<Monster> monsters,
            int inputCapacity) {
        return new Zone(TestMaps.emptyMap(mapId, maxPlayer), zoneId, monsters, inputCapacity, area());
    }

    private static AreaService area() {
        return new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
    }

    private static void submitFromBarrier(
            Zone zone,
            CyclicBarrier barrier,
            CountDownLatch submitted,
            CountDownLatch finished,
            AtomicReference<Thread> writer,
            AtomicBoolean oneWriter) {
        try {
            barrier.await();
            assertTrue(zone.submit(() -> {
                oneWriter.set(oneWriter.get() && Thread.currentThread().isVirtual());
                oneWriter.set(oneWriter.get() && writer.get() == Thread.currentThread());
                finished.countDown();
            }));
            submitted.countDown();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void awaitRuntimeState(Zone zone, ZoneWriter.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (zone.runtimeState() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, zone.runtimeState());
    }

    private static Monster monsterForTest() {
        MonsterManager factory = new MonsterManager(GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                MapTestSupport.canonicalMaps(),
                2,
                MonsterTestSupport.canonicalRepository()));
        return factory.createForMap(1).getFirst();
    }

    private static Session session(Player player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                TestServices.serverServices(), ClientConfig.defaults());
        if (player != null) {
            session.bindPlayer(player);
        }
        return session;
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return output;
        }

        @Override
        public String remoteAddress() {
            return "zone-test";
        }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}
