package com.project.game.testsupport;

import com.project.game.map.Zone;
import com.project.game.map.ZoneTestHooks;
import com.project.game.monster.Monster;
import com.project.game.monster.MonsterManager;
import com.project.game.network.Session;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Một Zone thật của map 1 (6 Monster mẫu) để test Player/Monster qua writer như lúc chạy. */
public final class TestZone {
    private final Zone zone;

    public TestZone() {
        MonsterManager monsters = new MonsterManager(GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                MonsterTestSupport.canonicalRepository()));
        zone = new Zone(1, 0, Integer.MAX_VALUE, monsters.createForMap(1),
                new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter()));
    }

    public Zone zone() {
        return zone;
    }

    /** Player mới (có Session) đứng ở map 1 / zone 0 tại (x, y) và đã vào Zone. */
    public Player join(int id, int x, int y) {
        return join(TestPlayers.at(TestPlayers.initial((long) id, id, "player" + id, 1), 1, 0, x, y));
    }

    public Player join(Player player) {
        Session session = GameplayTestSupport.session(player);
        if (!zone.enter(player)) {
            throw new AssertionError("player " + player.id() + " could not enter test zone");
        }
        try {
            GameplayTestSupport.drain(session);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return player;
    }

    public Monster monster(int id) {
        return ZoneTestHooks.call(zone, () -> zone.findMonster(id));
    }

    /** Chạy một hành động của Player trên writer và chờ kết quả. */
    public boolean run(Player player, BooleanSupplier action) {
        return ZoneTestHooks.run(zone, player, action);
    }

    /** Chạy đoạn code bất kỳ trên writer (Monster, setup...) và chờ xong. */
    public void runOnWriter(Runnable action) {
        ZoneTestHooks.call(zone, () -> {
            action.run();
            return null;
        });
    }

    public List<Integer> commands(Player player) {
        try {
            return GameplayTestSupport.commands(GameplayTestSupport.drain(player.session()));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
