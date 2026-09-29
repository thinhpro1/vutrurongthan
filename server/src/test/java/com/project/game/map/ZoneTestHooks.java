package com.project.game.map;

import java.util.Objects;
import java.util.function.Supplier;

/** Package bridge for runtime-mechanics tests outside the map package. */
public final class ZoneTestHooks {
    private ZoneTestHooks() {
    }

    public static boolean submit(Zone zone, Runnable action) {
        return Objects.requireNonNull(zone, "zone").submit(action);
    }

    public static <T> T call(Zone zone, Supplier<T> action) {
        return Objects.requireNonNull(zone, "zone").call(action);
    }
}
