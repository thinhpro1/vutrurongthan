package com.project.game.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerProfileTest {
    @Test
    void freshPlayerStartsAtLegacyMapZeroSpawn() {
        PlayerProfile player = PlayerProfile.initial("user01", 1, "alpha1", 0);

        assertEquals(0, player.mapId());
        assertEquals(0, player.zoneId());
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
    }
}
