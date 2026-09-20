package com.project.game.player;
import com.project.game.testsupport.TestPlayerProfiles;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileTest {
    @Test
    void freshPlayerUsesJavaInitializationDefaults() {
        PlayerProfile player = new PlayerInitialProfileFactory().createWithId(
                1, 101L, "alpha1", 0);

        assertEquals(101L, player.accountId());
        assertEquals(0, player.mapId());
        assertEquals(0, player.zoneId());
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
        assertEquals(200L, player.baseStats().hp());
        assertEquals(200L, player.baseStats().mp());
        assertEquals(200L, player.currentStats().maxHp());
        assertEquals(200L, player.currentStats().maxMp());
        assertEquals(200L, player.hp());
        assertEquals(200L, player.mp());
        assertEquals(12, player.baseStats().speed());
        assertEquals(12, player.currentStats().speed());
        assertEquals(player.baseStats().hp(), player.currentStats().maxHp());
        assertEquals(player.baseStats().mp(), player.currentStats().maxMp());
        assertEquals(player.baseStats().damage(), player.currentStats().damage());
    }

    @Test
    void withPositionChangesOnlyCoordinates() {
        PlayerProfile original = TestPlayerProfiles.initial(101L, 7, "alpha1", 0);

        PlayerProfile moved = original.withPosition(1337, 611);

        assertEquals(1337, moved.x());
        assertEquals(611, moved.y());
        assertEquals(original.accountId(), moved.accountId());
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
        PlayerProfile original = TestPlayerProfiles.initial(101L, 7, "alpha1", 0);

        PlayerProfile injured = original.withHp(90);

        assertEquals(90, injured.hp());
        assertEquals(original.accountId(), injured.accountId());
        assertEquals(original.id(), injured.id());
        assertEquals(original.name(), injured.name());
        assertEquals(original.mapId(), injured.mapId());
        assertEquals(original.zoneId(), injured.zoneId());
        assertEquals(original.x(), injured.x());
        assertEquals(original.y(), injured.y());
        assertEquals(original.currentStats().maxHp(), injured.currentStats().maxHp());
        assertEquals(original.currentStats().maxMp(), injured.currentStats().maxMp());
        assertEquals(original.mp(), injured.mp());
        assertEquals(original, injured.withHp(original.hp()));
    }

    @Test
    void withHpValidatesBoundsAndAllowsZero() {
        PlayerProfile player = TestPlayerProfiles.initial(101L, 1, "alpha1", 0);

        assertThrows(IllegalArgumentException.class, () -> player.withHp(-1));
        assertThrows(IllegalArgumentException.class,
                () -> player.withHp(player.currentStats().maxHp() + 1));
        assertEquals(0, player.withHp(0).hp());
    }

    @Test
    void withPotentialChangesOnlyPotential() {
        PlayerProfile before = TestPlayerProfiles.initial(101L, 7, "alpha1", 0);

        PlayerProfile after = before.withPotential(11L);

        assertEquals(11L, after.potential());
        assertEquals(before.accountId(), after.accountId());
        assertEquals(before.id(), after.id());
        assertEquals(before.name(), after.name());
        assertEquals(before.gender(), after.gender());
        assertEquals(before.power(), after.power());
        assertEquals(before.level(), after.level());
        assertEquals(before.exp(), after.exp());
        assertEquals(before.hp(), after.hp());
        assertEquals(before.mp(), after.mp());
        assertEquals(before.coin(), after.coin());
        assertEquals(before.mapId(), after.mapId());
        assertEquals(before.zoneId(), after.zoneId());
        assertEquals(before.x(), after.x());
        assertEquals(before.y(), after.y());
    }

    @Test
    void revivedAtRestoresVitalsAndChangesOnlyLocation() {
        PlayerProfile original = TestPlayerProfiles.initial(101L, 7, "reviver", 0)
                .withHp(0)
                .withPotential(123L);

        PlayerProfile revived = original.revivedAt(0, 0, 1250, 648);

        assertEquals(0, revived.mapId());
        assertEquals(0, revived.zoneId());
        assertEquals(1250, revived.x());
        assertEquals(648, revived.y());
        assertEquals(revived.currentStats().maxHp(), revived.hp());
        assertEquals(revived.currentStats().maxMp(), revived.mp());
        assertEquals(original.power(), revived.power());
        assertEquals(original.potential(), revived.potential());
        assertEquals(original.coin(), revived.coin());
        assertEquals(original.coinLock(), revived.coinLock());
        assertEquals(original.diamond(), revived.diamond());
        assertEquals(original.ruby(), revived.ruby());
        assertEquals(original.appearance().head(), revived.appearance().head());
        assertEquals(original.appearance().body(), revived.appearance().body());
        assertEquals(original.baseStats().damage(), revived.baseStats().damage());
    }

    @Test
    void rejectsInvalidDurableValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseStats(-1, 200, 10, 0, 0, 0, 5, 12));
        assertThrows(IllegalArgumentException.class,
                () -> new CurrentStats(200, 200, 10, 0, 0, 0, 5, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerProfile(0, 101L, "alpha1", 0, 1, 1, 1, 0,
                        new BaseStats(200, 200, 10, 0, 0, 0, 5, 12),
                        new CurrentStats(200, 200, 10, 0, 0, 0, 5, 12),
                        201, 200, new Appearance(5, 6, -1, -1, -1, -1, 0),
                        0, 10_000, 0, 25, 0, 0, 1250, 648));
    }

    @Test
    void playerCombatVitalsAndStatsUseIntContract() throws Exception {
        assertRecordComponentType(BaseStats.class, "hp", int.class);
        assertRecordComponentType(BaseStats.class, "mp", int.class);
        assertRecordComponentType(BaseStats.class, "damage", int.class);
        assertRecordComponentType(BaseStats.class, "armor", int.class);
        assertRecordComponentType(BaseStats.class, "constitution", int.class);
        assertRecordComponentType(CurrentStats.class, "maxHp", int.class);
        assertRecordComponentType(CurrentStats.class, "maxMp", int.class);
        assertRecordComponentType(CurrentStats.class, "damage", int.class);
        assertRecordComponentType(CurrentStats.class, "armor", int.class);
        assertRecordComponentType(CurrentStats.class, "constitution", int.class);
        assertRecordComponentType(PlayerProfile.class, "hp", int.class);
        assertRecordComponentType(PlayerProfile.class, "mp", int.class);

        assertFalse(java.util.Arrays.stream(PlayerProfile.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("withHp")
                        && method.getParameterTypes()[0] == long.class));
        assertFalse(java.util.Arrays.stream(PlayerProfile.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("withMp")
                        && method.getParameterTypes()[0] == long.class));
        assertTrue(java.util.Arrays.stream(PlayerProfile.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("withHp")
                        && method.getParameterTypes()[0] == int.class));
    }

    @Test
    void intStatContractSupportsMaximumIntegerValues() {
        BaseStats base = new BaseStats(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE, 12);
        CurrentStats current = new CurrentStats(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE, 12);

        assertEquals(Integer.MAX_VALUE, base.hp());
        assertEquals(Integer.MAX_VALUE, current.maxHp());
    }

    private static void assertRecordComponentType(
            Class<? extends Record> type, String name, Class<?> expected) {
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.getName().equals(name)) {
                assertEquals(expected, component.getType(), type.getSimpleName() + "." + name);
                return;
            }
        }
        throw new AssertionError("missing component " + type.getSimpleName() + "." + name);
    }
}
