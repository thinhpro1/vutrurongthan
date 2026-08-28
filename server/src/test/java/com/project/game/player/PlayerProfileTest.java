package com.project.game.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void withHpChangesOnlyHp() {
        PlayerProfile original = PlayerProfile.initial("user01", 7, "alpha1", 0);

        PlayerProfile injured = original.withHp(90);

        assertEquals(90, injured.hp());
        assertEquals(original.accountName(), injured.accountName());
        assertEquals(original.id(), injured.id());
        assertEquals(original.name(), injured.name());
        assertEquals(original.mapId(), injured.mapId());
        assertEquals(original.zoneId(), injured.zoneId());
        assertEquals(original.x(), injured.x());
        assertEquals(original.y(), injured.y());
        assertEquals(original.maxHp(), injured.maxHp());
        assertEquals(original.maxMp(), injured.maxMp());
        assertEquals(original.mp(), injured.mp());
        assertEquals(original, injured.withHp(original.hp()));
    }

    @Test
    void withHpValidatesBoundsAndAllowsZero() {
        PlayerProfile player = PlayerProfile.initial("user01", 1, "alpha1", 0);

        assertThrows(IllegalArgumentException.class, () -> player.withHp(-1));
        assertThrows(IllegalArgumentException.class, () -> player.withHp(player.maxHp() + 1));
        assertEquals(0, player.withHp(0).hp());
    }

    @Test
    void withPotentialChangesOnlyPotential() {
        PlayerProfile before = PlayerProfile.initial("user01", 7, "alpha1", 0);

        PlayerProfile after = before.withPotential(11L);

        assertEquals(11L, after.potential());
        assertEquals(before.accountName(), after.accountName());
        assertEquals(before.id(), after.id());
        assertEquals(before.name(), after.name());
        assertEquals(before.gender(), after.gender());
        assertEquals(before.power(), after.power());
        assertEquals(before.level(), after.level());
        assertEquals(before.hp(), after.hp());
        assertEquals(before.mp(), after.mp());
        assertEquals(before.coin(), after.coin());
        assertEquals(before.mapId(), after.mapId());
        assertEquals(before.zoneId(), after.zoneId());
        assertEquals(before.x(), after.x());
        assertEquals(before.y(), after.y());
    }
}
