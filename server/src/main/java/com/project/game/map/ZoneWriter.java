package com.project.game.map;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writer nội bộ của một Zone: một virtual thread chạy tuần tự input và nhịp update.
 *
 * <pre>
 * run():                                   // giống Zone.run() → update() của rongthan
 *     while (có input hoặc Zone còn Player):
 *         đến nhịp  → update()
 *         input đến → chạy input
 *     → FROZEN (thread kết thúc, input mới sẽ đánh thức)
 * </pre>
 * Nhịp kế tiếp tính từ lúc nhịp trước <b>kết thúc</b>: update chậm không bao giờ chạy nối
 * đuôi nhau, input luôn có lượt chạy và nhịp trễ không bị dồn lại. Không giữ state gameplay.
 */
final class ZoneWriter {
    static final int DEFAULT_INPUT_CAPACITY = 1024;
    private static final Logger LOGGER = Logger.getLogger(ZoneWriter.class.getName());
    private static final ThreadLocal<ZoneWriter> CURRENT_WRITER = new ThreadLocal<>();

    private final int mapId;
    private final int zoneId;
    private final ArrayBlockingQueue<Runnable> inputs;
    private final Object lock = new Object();
    private State state = State.FROZEN;
    private Thread worker;
    // Nhịp update; chỉ worker đọc ngoài lock. null = Zone chỉ chạy khi có input.
    private volatile Runnable update;
    private volatile BooleanSupplier awake;
    private volatile long periodNanos;
    private long nextUpdateNanos;
    // startUpdate đã chạy sau lần worker xét nhịp gần nhất: worker sắp nghỉ phải chạy lại.
    private boolean updateChanged;

    ZoneWriter(int mapId, int zoneId, int inputCapacity) {
        if (inputCapacity <= 0) {
            throw new IllegalArgumentException("inputCapacity must be positive");
        }
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.inputs = new ArrayBlockingQueue<>(inputCapacity);
    }

    /** Nhận diện đúng writer đang sở hữu tác vụ trên thread hiện tại. */
    boolean isCurrent() {
        return CURRENT_WRITER.get() == this;
    }

    /** Entry point bên ngoài không được chờ từ bất kỳ Zone writer nào. */
    static void requireOutsideWriter(String action) {
        if (CURRENT_WRITER.get() != null) {
            throw new IllegalStateException(
                    action + " must be called outside the Zone runtime writer");
        }
    }

    /** Xếp một tác vụ vào writer tuần tự của Zone mà không chặn khi hàng đợi đầy. */
    boolean submit(Runnable action) {
        Objects.requireNonNull(action, "action");
        synchronized (lock) {
            if (state == State.STOPPED || !inputs.offer(action)) {
                return false;
            }
            startIfNeededLocked();
            return true;
        }
    }

    /**
     * Bật nhịp update: worker gọi {@code update} mỗi {@code periodMillis} khi {@code awake}
     * còn đúng, và nghỉ (FROZEN) khi không còn input lẫn việc cần update.
     */
    void startUpdate(Runnable update, BooleanSupplier awake, long periodMillis) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(awake, "awake");
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("periodMillis must be positive");
        }
        synchronized (lock) {
            if (state == State.STOPPED) {
                return;
            }
            this.periodNanos = TimeUnit.MILLISECONDS.toNanos(periodMillis);
            this.awake = awake;
            this.update = update;
            updateChanged = true;
            startIfNeededLocked();
        }
    }

    /** Tắt nhịp update và chờ nhịp đang chạy (nếu có) xong; input vẫn được chạy như trước. */
    void stopUpdate() {
        update = null;
        if (isCurrent()) {
            return;
        }
        try {
            // Một input rỗng xếp sau nhịp đang chạy: khi nó xong, không còn nhịp nào chạy nữa.
            call(() -> null);
        } catch (RejectedExecutionException ignored) {
            // Writer đã dừng hẳn thì cũng không còn nhịp nào.
        }
    }

    /** Chạy tác vụ trên writer và từ chối ngay khi hàng đợi giới hạn đã đầy. */
    <T> T tryCall(Supplier<T> action) {
        return executeCall(action, false);
    }

    /** Chạy tác vụ bắt buộc trên writer, chờ chỗ trống nếu hàng đợi đã đầy. */
    <T> T call(Supplier<T> action) {
        return executeCall(action, true);
    }

    private <T> T executeCall(Supplier<T> action, boolean required) {
        Objects.requireNonNull(action, "action");
        boolean onRuntimeWorker;
        synchronized (lock) {
            if (state == State.STOPPED) {
                throw new RejectedExecutionException(
                        "Zone runtime is stopped for map " + mapId + " zone " + zoneId);
            }
            onRuntimeWorker = worker == Thread.currentThread();
        }
        if (onRuntimeWorker) {
            return action.get();
        }

        Call<T> call = new Call<>(action, mapId, zoneId);
        if (required) {
            boolean interruptedWhileAdmitting = submitRequired(call);
            return call.await(interruptedWhileAdmitting);
        }
        if (!submit(call)) {
            throw new RejectedExecutionException(
                    "Zone runtime rejected action for map " + mapId + " zone " + zoneId);
        }
        return call.await(false);
    }

    private boolean submitRequired(Call<?> action) {
        boolean interrupted = false;
        synchronized (lock) {
            while (state != State.STOPPED && !inputs.offer(action)) {
                try {
                    lock.wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (state == State.STOPPED) {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                throw new RejectedExecutionException(
                        "Zone runtime is stopped for map " + mapId + " zone " + zoneId);
            }
            startIfNeededLocked();
        }
        return interrupted;
    }

    State state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Dừng vĩnh viễn runtime của Zone. Tác vụ đang chạy được phép hoàn tất;
     * các lời gọi đang xếp hàng bị từ chối và đánh thức bên gọi, các tác vụ khác bị loại bỏ.
     */
    void stop() {
        update = null;
        synchronized (lock) {
            state = State.STOPPED;
            for (Runnable action : inputs) {
                if (action instanceof Call<?> call) {
                    call.cancel();
                }
            }
            inputs.clear();
            lock.notifyAll();
        }
    }

    private void startIfNeededLocked() {
        if (worker == null) {
            startLocked();
        }
    }

    private void startLocked() {
        Thread nextWorker = Thread.ofVirtual().unstarted(this::run);
        worker = nextWorker;
        state = State.ACTIVE;
        nextWorker.start();
    }

    private void run() {
        CURRENT_WRITER.set(this);
        try {
            try {
                while (true) {
                    Runnable action = nextAction();
                    if (action == null) {
                        return;
                    }
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        LOGGER.log(
                                Level.WARNING,
                                "Zone runtime action failed for map " + mapId + " zone " + zoneId,
                                exception);
                    }
                }
            } finally {
                synchronized (lock) {
                    if (worker == Thread.currentThread()) {
                        worker = null;
                        if (state == State.STOPPED) {
                            inputs.clear();
                        } else if (inputs.isEmpty() && !updateChanged) {
                            state = State.FROZEN;
                        } else {
                            startLocked();
                        }
                    }
                }
            }
        } finally {
            CURRENT_WRITER.remove();
        }
    }

    /** Nhịp update khi đến hạn, hoặc input kế tiếp; null khi Zone được nghỉ hoặc đã dừng. */
    private Runnable nextAction() {
        while (true) {
            synchronized (lock) {
                if (state == State.STOPPED) {
                    inputs.clear();
                    return null;
                }
                updateChanged = false;
            }
            boolean ticking = update != null && awake.getAsBoolean();
            long now = System.nanoTime();
            if (ticking && now - nextUpdateNanos >= 0) {
                return this::runUpdate;
            }
            synchronized (lock) {
                Runnable input = inputs.poll();
                if (input != null) {
                    lock.notifyAll();
                    return input;
                }
            }
            if (!ticking) {
                return null;
            }
            Runnable input = waitForInput(nextUpdateNanos - now);
            if (input != null) {
                return input;
            }
        }
    }

    /** Một nhịp update; nhịp kế tiếp được hẹn từ lúc nhịp này kết thúc. */
    private void runUpdate() {
        try {
            Runnable tick = update;
            if (tick != null) {
                tick.run();
            }
        } finally {
            nextUpdateNanos = System.nanoTime() + periodNanos;
        }
    }

    /** Chờ input tới hạn nhịp kế tiếp; dùng hàng đợi (không pin carrier của virtual thread). */
    private Runnable waitForInput(long waitNanos) {
        Runnable input;
        try {
            input = inputs.poll(waitNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            return null;
        }
        if (input == null) {
            return null;
        }
        synchronized (lock) {
            if (state == State.STOPPED) {
                // stop() đã chạy sau khi input rời hàng đợi: báo cho bên gọi thay vì bỏ rơi.
                if (input instanceof Call<?> call) {
                    call.cancel();
                }
                return null;
            }
            lock.notifyAll();
        }
        return input;
    }

    private static final class Call<T> implements Runnable {
        private final Supplier<T> action;
        private final String rejectionMessage;
        private final CountDownLatch completed = new CountDownLatch(1);
        private T result;
        private Throwable failure;

        private Call(Supplier<T> action, int mapId, int zoneId) {
            this.action = Objects.requireNonNull(action, "action");
            this.rejectionMessage =
                    "Zone runtime stopped for map " + mapId + " zone " + zoneId;
        }

        @Override
        public void run() {
            try {
                result = action.get();
            } catch (RuntimeException | Error exception) {
                failure = exception;
                throw exception;
            } finally {
                completed.countDown();
            }
        }

        private void cancel() {
            failure = new RejectedExecutionException(rejectionMessage);
            completed.countDown();
        }

        private T await(boolean interrupted) {
            while (true) {
                try {
                    completed.await();
                    break;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            Throwable exception = failure;
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            return result;
        }
    }

    enum State {
        ACTIVE,
        FROZEN,
        STOPPED
    }
}
