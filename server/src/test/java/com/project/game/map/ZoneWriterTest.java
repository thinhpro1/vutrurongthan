package com.project.game.map;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
}
