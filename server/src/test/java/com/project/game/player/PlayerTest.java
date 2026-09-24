package com.project.game.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTest {
    @Test
    void freshPlayerUsesJavaInitializationDefaults() {
        Player player = Player.createWithId(1, 101L, "Alpha1", 0);

        assertEquals(101L, player.accountId());
        assertEquals("alpha1", player.name());
        assertEquals(0, player.mapId());
        assertEquals(0, player.zoneId());
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
        assertEquals(200, player.baseStats().hp());
        assertEquals(200, player.currentStats().maxHp());
        assertEquals(200, player.hp());
        assertEquals(12, player.currentStats().speed());
    }

    @Test
    void moveMutatesSamePlayerAndPreservesOtherState() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        player.changeMap(1, 2, 90, 1008);
        player.injure(77);
        long potential = player.addPotential(99);

        assertTrue(player.move(1337, 611));
        assertEquals(1337, player.x());
        assertEquals(611, player.y());
        assertEquals(1, player.mapId());
        assertEquals(2, player.zoneId());
        assertEquals(123, player.hp());
        assertEquals(potential, player.potential());
    }

    @Test
    void deadPlayerCannotMove() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        player.injure(Long.MAX_VALUE);

        assertFalse(player.move(1, 2));
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
    }

    @Test
    void injureClampsHpAtZeroAndRejectsNegativeDamage() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);

        assertEquals(150, player.injure(50));
        assertEquals(0, player.injure(Long.MAX_VALUE));
        assertTrue(player.isDead());
        assertThrows(IllegalArgumentException.class, () -> player.injure(-1));
    }

    @Test
    void reviveRestoresVitalsAndChangesOnlyLocation() {
        Player player = Player.createWithId(7, 101L, "reviver", 0);
        player.injure(Long.MAX_VALUE);
        player.addPotential(123);
        long potential = player.potential();
        Appearance appearance = player.appearance();
        BaseStats baseStats = player.baseStats();

        player.revive(1, 2, 333, 444);

        assertEquals(200, player.hp());
        assertEquals(200, player.mp());
        assertEquals(1, player.mapId());
        assertEquals(2, player.zoneId());
        assertEquals(333, player.x());
        assertEquals(444, player.y());
        assertEquals(potential, player.potential());
        assertSame(appearance, player.appearance());
        assertSame(baseStats, player.baseStats());
    }

    @Test
    void changeMapChangesOnlyLocation() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        int hp = player.hp();
        long power = player.power();

        player.changeMap(3, 4, 5, 6);

        assertEquals(3, player.mapId());
        assertEquals(4, player.zoneId());
        assertEquals(5, player.x());
        assertEquals(6, player.y());
        assertEquals(hp, player.hp());
        assertEquals(power, player.power());
    }

    @Test
    void addPotentialSaturatesWithoutChangingPower() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        long power = player.power();
        player.addPotential(Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, player.potential());
        assertEquals(power, player.power());
        assertThrows(IllegalArgumentException.class, () -> player.addPotential(-1));
    }

    @Test
    void saveDataCaptureIsStableAfterPlayerMutation() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        PlayerSaveData saved = PlayerSaveData.capture(player);

        player.move(1, 2);
        player.injure(50);
        player.addPotential(20);

        assertEquals(1250, saved.x());
        assertEquals(648, saved.y());
        assertEquals(200, saved.hp());
        assertEquals(1, saved.potential());
        assertNotSame(player, saved);
    }
}
