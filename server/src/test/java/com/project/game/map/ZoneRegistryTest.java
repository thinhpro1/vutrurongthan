package com.project.game.map;

import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ZoneRegistryTest {
    @Test
    void getOrCreateReusesOneZoneAndRetainsItWhenEmpty() {
        ZoneRegistry registry = registry();

        Zone first = registry.getOrCreate(1, 0);
        assertSame(first, registry.getOrCreate(1, 0));
        assertSame(first, registry.find(1, 0));
        assertEquals(1, registry.snapshot().size());
    }

    @Test
    void differentKeysHaveDistinctZonesAndIndependentSeeds() {
        ZoneRegistry registry = registry();

        Zone zone0 = registry.getOrCreate(1, 0);
        Zone zone1 = registry.getOrCreate(1, 1);

        assertNotSame(zone0, zone1);
        assertEquals(zone0.monsterSnapshots(), zone1.monsterSnapshots());
    }

    @Test
    void findAndSnapshotNeverCreateMissingZones() {
        ZoneRegistry registry = registry();

        assertNull(registry.find(1, 0));
        assertEquals(0, registry.snapshot().size());

        Zone created = registry.getOrCreate(0, 0);
        assertNotNull(created);
        assertSame(created, registry.find(0, 0));
        assertEquals(1, registry.snapshot().size());
    }

    private static ZoneRegistry registry() {
        return new ZoneRegistry(new MonsterRuntimeFactory(
                GameResources.fromFrameRoot(java.nio.file.Path.of("resources", "json"))));
    }
}
