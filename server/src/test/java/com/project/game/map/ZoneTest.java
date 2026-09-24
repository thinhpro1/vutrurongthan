package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.monster.MonsterFactory;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import com.project.game.testsupport.TestPlayerProfiles;

import com.project.game.testsupport.TestServices;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTest {
    @Test
    void startsEmptyAndTracksBoundPlayer() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        Session session = session(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));

        assertEquals(0, zone.size());
        assertTrue(zone.add(session));
        assertEquals(1, zone.size());
        assertTrue(zone.containsPlayer(7));
        assertTrue(zone.remove(session));
        assertFalse(zone.containsPlayer(7));
        assertEquals(0, zone.size());
    }

    @Test
    void duplicateSameSessionIsIdempotent() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        Session session = session(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));

        assertTrue(zone.add(session));
        assertFalse(zone.add(session));
        assertEquals(1, zone.size());
    }

    @Test
    void differentSessionCannotReplaceSamePlayerId() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        Session second = session(TestPlayerProfiles.initial(2L, 7, "alpha2", 0));

        assertTrue(zone.add(first));
        assertFalse(zone.add(second));
        assertEquals(List.of(first), zone.snapshot());
    }

    @Test
    void differentSessionWithSamePlayerIdIsAnIdentityConflict() {
        Zone zone = new Zone(0, 0, 2, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        Session second = session(TestPlayerProfiles.initial(2L, 7, "alpha2", 0));

        assertEquals(Zone.JoinStatus.ADDED, zone.addAndSnapshot(first).status());
        assertEquals(Zone.JoinStatus.PLAYER_ID_CONFLICT, zone.addAndSnapshot(second).status());
        assertEquals(1, zone.size());
        assertEquals(List.of(first), zone.snapshot());
        assertTrue(zone.contains(first));
        assertFalse(zone.contains(second));
        assertTrue(zone.canAccept(first));
        assertFalse(zone.canAccept(second));
    }

    @Test
    void snapshotIsImmutable() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        zone.add(session(TestPlayerProfiles.initial(1L, 7, "alpha1", 0)));

        List<Session> snapshot = zone.snapshot();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void requiresBoundPlayer() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        assertThrows(IllegalStateException.class, () -> zone.add(session(null)));
    }

    @Test
    void requiresExplicitMonsterSeed() {
        assertThrows(
                NoSuchMethodException.class,
                () -> Zone.class.getConstructor(int.class, int.class));
    }

    @Test
    void atomicallyReturnsExistingMembersWhileAddingNewMember() {
        Zone zone = new Zone(0, 0, Integer.MAX_VALUE, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 0));
        Session second = session(TestPlayerProfiles.initial(2L, 2, "beta22", 0));
        zone.add(first);

        Zone.JoinResult result = zone.addAndSnapshot(second);

        assertEquals(Zone.JoinStatus.ADDED, result.status());
        assertEquals(List.of(first), result.existing());
        assertEquals(2, zone.size());
        assertTrue(zone.snapshot().containsAll(List.of(first, second)));
    }

    @Test
    void maxPlayerDistinguishesDuplicateFromFull() {
        Zone zone = new Zone(0, 0, 1, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 0));
        Session second = session(TestPlayerProfiles.initial(2L, 2, "beta22", 0));

        assertEquals(Zone.JoinStatus.ADDED, zone.addAndSnapshot(first).status());
        assertEquals(Zone.JoinStatus.ALREADY_PRESENT, zone.addAndSnapshot(first).status());
        assertEquals(Zone.JoinStatus.FULL, zone.addAndSnapshot(second).status());
        assertEquals(1, zone.size());
        assertEquals(1, zone.maxPlayer());
    }

    @Test
    void concurrentAdmissionNeverExceedsMaxPlayer() throws Exception {
        Zone zone = new Zone(0, 0, 1, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 0));
        Session second = session(TestPlayerProfiles.initial(2L, 2, "beta22", 0));
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicInteger admitted = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread firstJoin = Thread.ofVirtual().start(() -> admitAtBarrier(
                start, zone, first, admitted, failure));
        Thread secondJoin = Thread.ofVirtual().start(() -> admitAtBarrier(
                start, zone, second, admitted, failure));

        start.await();
        firstJoin.join();
        secondJoin.join();

        if (failure.get() != null) {
            throw new AssertionError("concurrent admission failed", failure.get());
        }
        assertEquals(1, admitted.get());
        assertEquals(1, zone.size());
        assertEquals(1, zone.maxPlayer());
    }

    @Test
    void runtimeStartsFrozenWithoutCreatingAnActiveWorker() {
        Zone zone = new Zone(1, 0, 10, List.of());

        assertEquals(Zone.RuntimeState.FROZEN, zone.runtimeState());
    }

    @Test
    void lifecycleControlsStayPackagePrivateExceptSubmitBoundary() throws Exception {
        assertFalse(Modifier.isPublic(Zone.RuntimeState.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("runtimeState").getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("stopRuntime").getModifiers()));
        assertFalse(Modifier.isPublic(
                Zone.class.getDeclaredMethod("call", Supplier.class).getModifiers()));
        assertTrue(Modifier.isPublic(
                Zone.class.getDeclaredMethod("submit", Runnable.class).getModifiers()));
    }

    @Test
    void runtimeRejectsNullActionsAndInvalidInputCapacity() {
        Zone zone = new Zone(1, 0, 10, List.of());

        assertThrows(NullPointerException.class, () -> zone.submit(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Zone(1, 0, 10, List.of(), 0));
    }

    @Test
    void queuedActionsRunInFifoOrderOnOneVirtualWriter() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
        assertEquals(Zone.RuntimeState.ACTIVE, zone.runtimeState());

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
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);

        assertEquals(List.of(1, 2, 3), order);
        assertTrue(allVirtual.get());
        assertTrue(oneWriter.get());
    }

    @Test
    void simultaneousSubmissionsShareOneVirtualWriter() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);

        assertTrue(oneWriter.get());
    }

    @Test
    void runtimeReturnsToFrozenAfterQueueDrains() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
        CountDownLatch completed = new CountDownLatch(1);

        assertTrue(zone.submit(completed::countDown));
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
    }

    @Test
    void synchronousCallWaitsForQueuedActionAndReturnsResult() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
        Zone zone = new Zone(1, 0, 10, List.of());
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();

        assertTrue(zone.submit(() -> {
            result.set(zone.call(() -> 42));
            finished.countDown();
        }));

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
        assertEquals(42, result.get());
    }

    @Test
    void acceptedCallRestoresCallerInterruptAfterCompletion() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
    void stoppedRuntimeRejectsSynchronousCall() {
        Zone zone = new Zone(1, 0, 10, List.of());
        zone.stopRuntime();

        assertThrows(RejectedExecutionException.class, () -> zone.call(() -> 42));
    }

    @Test
    void stoppedRuntimeRejectsAcceptedCallInsteadOfLeavingCallerWaiting() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
        awaitRuntimeState(zone, Zone.RuntimeState.STOPPED);
    }

    @Test
    void freezeWakeKeepsExistingMonsterRuntimeState() throws Exception {
        Monster monster = monsterForTest();
        Zone zone = new Zone(1, 0, 10, List.of(monster));
        CountDownLatch damaged = new CountDownLatch(1);
        CountDownLatch woke = new CountDownLatch(1);

        assertTrue(zone.submit(() -> {
            zone.damageMonster(monster.id(), 1, 10, 0);
            damaged.countDown();
        }));
        assertTrue(damaged.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
        assertEquals(290, monster.snapshot().hp());

        assertTrue(zone.submit(woke::countDown));
        assertTrue(woke.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
        assertEquals(290, monster.snapshot().hp());
    }

    @Test
    void boundedRuntimeQueueRejectsOverflowWithoutBlocking() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of(), 1);
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
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
    }

    @Test
    void runtimeTaskFailureDoesNotStrandLifecycle() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
        CountDownLatch continued = new CountDownLatch(1);

        assertTrue(zone.submit(() -> {
            throw new IllegalStateException("expected test failure");
        }));
        assertTrue(zone.submit(continued::countDown));

        assertTrue(continued.await(5, TimeUnit.SECONDS));
        awaitRuntimeState(zone, Zone.RuntimeState.FROZEN);
    }

    @Test
    void stoppedRuntimeRejectsNewActionsAndDiscardsPendingActions() throws Exception {
        Zone zone = new Zone(1, 0, 10, List.of());
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
        assertEquals(Zone.RuntimeState.STOPPED, zone.runtimeState());
        assertFalse(zone.submit(() -> {
            throw new AssertionError("stopped action must not run");
        }));

        releaseFirst.countDown();
        awaitRuntimeState(zone, Zone.RuntimeState.STOPPED);
        assertFalse(pendingRan.get());
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

    private static void awaitRuntimeState(Zone zone, Zone.RuntimeState expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (zone.runtimeState() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, zone.runtimeState());
    }

    private static Monster monsterForTest() {
        MonsterFactory factory = new MonsterFactory(GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                MapTestSupport.canonicalMaps(),
                2,
                MonsterTestSupport.canonicalRepository()));
        return factory.createForMap(1).getFirst();
    }

    private static void admitAtBarrier(
            CyclicBarrier start,
            Zone zone,
            Session session,
            AtomicInteger admitted,
            AtomicReference<Throwable> failure) {
        try {
            start.await();
            if (zone.add(session)) {
                admitted.incrementAndGet();
            }
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static Session session(PlayerProfile player) {
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
