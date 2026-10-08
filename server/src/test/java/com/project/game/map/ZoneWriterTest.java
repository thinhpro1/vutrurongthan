package com.project.game.map;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneWriterTest {
    @Test
    void currentWriterIsExactAndOutsideGuardRejectsAnyWriter() {
        ZoneWriter first = new ZoneWriter(1, 0, 1);
        ZoneWriter second = new ZoneWriter(1, 1, 1);

        try {
            assertFalse(first.isCurrent());
            assertFalse(second.isCurrent());
            ZoneWriter.requireOutsideWriter("test");

            first.call(() -> {
                assertTrue(first.isCurrent());
                assertFalse(second.isCurrent());
                assertThrows(IllegalStateException.class,
                        () -> ZoneWriter.requireOutsideWriter("test"));
                return null;
            });

            second.call(() -> {
                assertFalse(first.isCurrent());
                assertTrue(second.isCurrent());
                assertThrows(IllegalStateException.class,
                        () -> ZoneWriter.requireOutsideWriter("test"));
                return null;
            });

            assertFalse(first.isCurrent());
            assertFalse(second.isCurrent());
            ZoneWriter.requireOutsideWriter("test");
        } finally {
            first.stop();
            second.stop();
        }
    }

    @Test
    void nestedCallAndTryCallRunInlineOnTheSameVirtualWriter() throws Exception {
        ZoneWriter writer = new ZoneWriter(1, 0, 1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            assertTrue(writer.submit(() -> {
                try {
                    Thread current = Thread.currentThread();
                    assertTrue(current.isVirtual());
                    assertSame(current, writer.call(Thread::currentThread));
                    assertSame(current, writer.tryCall(Thread::currentThread));
                } catch (Throwable exception) {
                    failure.set(exception);
                } finally {
                    finished.countDown();
                }
            }));

            assertTrue(finished.await(5, TimeUnit.SECONDS));
            if (failure.get() != null) {
                throw new AssertionError("nested writer call failed", failure.get());
            }
        } finally {
            writer.stop();
        }
    }

    @Test
    void slowUpdateDoesNotStarveQueuedInput() throws Exception {
        ZoneWriter writer = new ZoneWriter(1, 0, 16);
        CountDownLatch firstTick = new CountDownLatch(1);
        AtomicInteger ticks = new AtomicInteger();
        try {
            // Mỗi nhịp dài hơn chu kỳ: trước đây writer chạy nhịp nối nhịp và bỏ đói input.
            writer.startUpdate(() -> {
                ticks.incrementAndGet();
                firstTick.countDown();
                sleep(150);
            }, () -> true, 1);
            assertTrue(firstTick.await(5, TimeUnit.SECONDS));

            CountDownLatch ran = new CountDownLatch(1);
            AtomicInteger ticksBeforeInput = new AtomicInteger(-1);
            assertTrue(writer.submit(() -> {
                ticksBeforeInput.set(ticks.get());
                ran.countDown();
            }));

            assertTrue(ran.await(2, TimeUnit.SECONDS), "queued input starved by back-to-back updates");
            assertTrue(ticksBeforeInput.get() <= 2,
                    "input waited for " + ticksBeforeInput.get() + " updates");
        } finally {
            writer.stop();
        }
    }

    @Test
    void startUpdateWhileWorkerIsFreezingRestartsWithoutInput() throws Exception {
        ZoneWriter writer = new ZoneWriter(1, 0, 16);
        CountDownLatch deciding = new CountDownLatch(1);
        CountDownLatch decide = new CountDownLatch(1);
        CountDownLatch restartedTick = new CountDownLatch(1);
        try {
            // Worker đang quyết định nghỉ (Zone trống) thì nhịp update được bật lại.
            writer.startUpdate(() -> { }, () -> {
                deciding.countDown();
                await(decide);
                return false;
            }, 1);
            assertTrue(deciding.await(5, TimeUnit.SECONDS));

            writer.startUpdate(restartedTick::countDown, () -> true, 1);
            decide.countDown();

            assertTrue(restartedTick.await(2, TimeUnit.SECONDS),
                    "update was enabled but the freezing worker never restarted");
        } finally {
            writer.stop();
        }
    }

    @Test
    void repeatedStopAndStartKeepsUpdatingWithoutInput() throws Exception {
        ZoneWriter writer = new ZoneWriter(1, 0, 16);
        AtomicInteger ticks = new AtomicInteger();
        try {
            for (int cycle = 0; cycle < 200; cycle++) {
                writer.stopUpdate();
                int before = ticks.get();
                writer.startUpdate(ticks::incrementAndGet, () -> true, 1);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (ticks.get() == before && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertTrue(ticks.get() > before, "no update after restart in cycle " + cycle);
            }
        } finally {
            writer.stop();
        }
    }

    @Test
    void noUpdateRunsAfterStopUpdateReturns() throws Exception {
        ZoneWriter writer = new ZoneWriter(1, 0, 16);
        AtomicInteger ticks = new AtomicInteger();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean lateTick = new AtomicBoolean();
        try {
            writer.startUpdate(() -> {
                if (stopped.get()) {
                    lateTick.set(true);
                }
                ticks.incrementAndGet();
                sleep(20);
            }, () -> true, 1);
            while (ticks.get() < 3) {
                Thread.onSpinWait();
            }

            writer.stopUpdate();
            stopped.set(true);
            int afterStop = ticks.get();
            Thread.sleep(200);

            assertFalse(lateTick.get(), "an update ran after stopUpdate returned");
            assertEquals(afterStop, ticks.get());
            assertEquals(ZoneWriter.State.FROZEN, writer.state());
        } finally {
            writer.stop();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
