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

    @Test
    void withPositionChangesOnlyCoordinates() {
        PlayerProfile original = PlayerProfile.initial("user01", 7, "alpha1", 0);

        PlayerProfile moved = original.withPosition(1337, 611);

        assertEquals(1337, moved.x());
        assertEquals(611, moved.y());
        assertEquals(original.accountName(), moved.accountName());
        assertEquals(original.id(), moved.id());
        assertEquals(original.name(), moved.name());
        assertEquals(original.gender(), moved.gender());
        assertEquals(original.mapId(), moved.mapId());
        assertEquals(original.zoneId(), moved.zoneId());
        assertEquals(original.hp(), moved.hp());
        assertEquals(original.mp(), moved.mp());
        assertEquals(original, moved.withPosition(original.x(), original.y()));
    }
}
