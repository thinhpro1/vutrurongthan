package com.project.game.monster;

import com.project.game.map.ZoneRegistry;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MonsterServiceTest {
    @Test
    void snapshotsCreateRuntimeZoneButLifecycleTickOnlyVisitsExistingZones() {
        ZoneRegistry zones = new ZoneRegistry(new MonsterRuntimeFactory(
                GameResources.fromFrameRoot(java.nio.file.Path.of("resources", "json"))));
        MonsterService monsters = new MonsterService(
                zones, new MonsterPacketWriter(), new PlayerPacketWriter());

        assertEquals(0, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(0, zones.snapshot().size());

        assertNotNull(monsters.monsterSnapshots(1, 0));
        assertEquals(1, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(1, zones.snapshot().size());
    }
}
