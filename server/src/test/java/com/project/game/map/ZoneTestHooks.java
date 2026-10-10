package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.player.Player;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Cầu nối cho test ngoài package map: chạy hành động đồng bộ qua cửa vào của Zone. */
public final class ZoneTestHooks {
    private ZoneTestHooks() {
    }

    public static boolean submit(Zone zone, Runnable action) {
        return Objects.requireNonNull(zone, "zone").submit(action);
    }

    public static <T> T call(Zone zone, Supplier<T> action) {
        return Objects.requireNonNull(zone, "zone").call(action);
    }

    /** Như zone.post nhưng chờ xong; false nếu Player không còn ở Zone hoặc hàng đợi đầy. */
    public static boolean run(Zone zone, Player player, BooleanSupplier action) {
        return Objects.requireNonNull(zone, "zone").run(player, action);
    }

    /** Chờ mọi việc đã xếp trước đó trên writer chạy xong. */
    public static void drain(Zone zone) {
        call(zone, () -> null);
    }

    public static boolean move(Zone zone, Session session, int x, int y) {
        Player player = session.player();
        return run(zone, player, () -> player.move(x, y));
    }

    public static boolean useSkill(Zone zone, Session session, int monsterId) {
        Player player = session.player();
        return run(zone, player, () -> player.useSkill(0, monsterId));
    }

    /** Đánh thẳng một Monster (không qua bước useSkill). */
    public static boolean attackMonster(Zone zone, Session session, int monsterId, long nowMillis) {
        Player player = session.player();
        return run(zone, player, () -> player.attackMonster(zone.findMonster(monsterId), nowMillis));
    }
}
