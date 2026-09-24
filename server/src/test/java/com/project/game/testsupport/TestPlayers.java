package com.project.game.testsupport;

import com.project.game.player.Player;

public final class TestPlayers {
    private TestPlayers() {
    }

    public static Player initial(long accountId, int id, String name, int gender) {
        return Player.createWithId(id, accountId, name, gender);
    }

    public static Player at(Player player, int mapId, int zoneId, int x, int y) {
        player.changeMap(mapId, zoneId, x, y);
        return player;
    }

    public static Player hp(Player player, int value) {
        if (value < 0 || value > player.currentStats().maxHp()) {
            throw new IllegalArgumentException("test hp is outside bounds");
        }
        player.injure(player.hp() - value);
        return player;
    }
}
