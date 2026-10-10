package com.project.game.map;

import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.player.Player;
import com.project.game.testsupport.MonsterSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Cầu nối cho test: chạy việc trên thread của Zone và chờ xong. */
public final class ZoneTestHooks {
    private ZoneTestHooks() {
    }

    public static boolean submit(Zone zone, Runnable action) {
        return Objects.requireNonNull(zone, "zone").submit(action);
    }

    public static <T> T call(Zone zone, Supplier<T> action) {
        return Objects.requireNonNull(zone, "zone").call(action);
    }

    /** Như zone.post nhưng chờ xong; false nếu Player không ở Zone, đang tải map, hoặc hàng đợi đầy. */
    public static boolean run(Zone zone, Player player, BooleanSupplier action) {
        return Objects.requireNonNull(zone, "zone").run(player, action);
    }

    /** Player rời Zone (như lúc đi map khác) và chờ xong. */
    public static void leave(Zone zone, Player player) {
        call(zone, () -> {
            zone.leave(player);
            return null;
        });
    }

    /** Chờ mọi việc đã xếp trước đó trên Zone chạy xong. */
    public static void drain(Zone zone) {
        call(zone, () -> null);
    }

    /** Player vào Zone và tải map xong ngay (như client gửi FINISH_LOAD_MAP). */
    public static boolean join(Zone zone, Player player) {
        call(zone, () -> {
            zone.enter(player);
            return null;
        });
        zone.finishLoadMap(player);
        drain(zone);
        return player.zone() == zone;
    }

    /** Đăng nhập xong: vào Zone theo vị trí của Player, tải map xong; false nếu không vào được. */
    public static boolean joinGame(MapManager maps, Session session) {
        Player player = session.player();
        if (player.zone() == null && player.travelingTo() == null) {
            maps.enterGame(player);
        }
        arrive(player);
        return session.zone() != null;
    }

    /** Chờ Player đi tới Zone mới (đã nhận MAP_INFO) nhưng chưa báo tải xong map. */
    public static void awaitTravel(Player player) {
        Zone next = player.travelingTo();
        while (next != null) {
            drain(next);
            next = player.travelingTo();
        }
        // enter() xóa travelingTo trước khi gửi MAP_INFO: chờ Zone hiện tại làm xong việc đó.
        Zone current = player.zone();
        if (current != null) {
            drain(current);
        }
    }

    /** Chờ Player đi tới nơi (nếu đang đi) rồi tải map xong. */
    public static void arrive(Player player) {
        Zone next = player.travelingTo();
        while (next != null) {
            drain(next);
            next = player.travelingTo();
        }
        Zone zone = player.zone();
        if (zone != null) {
            zone.finishLoadMap(player);
            drain(zone);
        }
    }

    public static List<Player> players(Zone zone) {
        return call(zone, () -> List.copyOf(zone.players()));
    }

    public static List<MonsterSnapshot> monsterSnapshots(Zone zone) {
        return call(zone, () -> {
            List<MonsterSnapshot> snapshots = new ArrayList<>();
            for (Monster monster : zone.monsters()) {
                snapshots.add(MonsterSnapshot.of(monster));
            }
            return snapshots;
        });
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
