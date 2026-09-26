package com.project.game.monster;

import com.project.game.map.Zone;
import com.project.game.testsupport.GameplayServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.project.game.testsupport.GameplayTestSupport.mapsWithMonsters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterSnapshotTest {
    @Test
    void snapshotsReadExistingPublicZonesWithoutJoiningAPlayer() {
        GameplayServices maps = mapsWithMonsters();

        List<MonsterSnapshot> monsters = maps.monsterSnapshots(1, 0);

        assertEquals(List.of(101, 102, 103, 104, 105, 106),
                monsters.stream().map(MonsterSnapshot::id).toList());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void snapshotReadsNeverCreateAnAbsentZone() {
        GameplayServices maps = mapsWithMonsters();
        Zone existing = maps.findZone(1, 0);

        assertThrows(IllegalArgumentException.class, () -> maps.monsterSnapshots(1, 3));

        assertEquals(existing, maps.findZone(1, 0));
        assertNull(maps.findZone(1, 3));
    }

    @Test
    void eachExistingZoneHasIndependentSnapshotState() {
        GameplayServices maps = mapsWithMonsters();

        assertEquals(maps.monsterSnapshots(1, 0), maps.monsterSnapshots(1, 1));
        assertTrue(maps.monsterSnapshots(0, 0).isEmpty());
    }
}
