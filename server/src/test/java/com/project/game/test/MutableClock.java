package com.project.game.test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class MutableClock extends Clock {
    private final AtomicLong millis;
    private final ZoneId zone;

    public MutableClock(long initialMillis) {
        this(new AtomicLong(initialMillis), ZoneOffset.UTC);
    }

    private MutableClock(AtomicLong millis, ZoneId zone) {
        this.millis = Objects.requireNonNull(millis, "millis");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public void advanceMillis(long deltaMillis) {
        if (deltaMillis < 0L) {
            throw new IllegalArgumentException("deltaMillis must be non-negative");
        }
        millis.updateAndGet(current -> Math.addExact(current, deltaMillis));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(millis, Objects.requireNonNull(zone, "zone"));
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
        return millis.get();
    }
}
