package com.project.game.monster;

import com.project.game.service.ResourceService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterRuntimeFactoryTest {
    private static ResourceService resources() {
        return ResourceService.fromFrameRoot(
                Path.of("resources", "json"));
    }

    @Test
    void createsCanonicalRuntimeMonstersForSupportedMaps() {
        MonsterRuntimeFactory factory =
                new MonsterRuntimeFactory(resources());

        assertTrue(factory.createForMap(0).isEmpty());

        List<RuntimeMonster> map1 = factory.createForMap(1);
        assertEquals(6, map1.size());

        List<MonsterSnapshot> snapshots =
                map1.stream().map(RuntimeMonster::snapshot).toList();

        assertEquals(
                List.of(0, 1, 2, 3, 4, 5),
                snapshots.stream().map(MonsterSnapshot::id).toList());
        assertEquals(
                List.of(975, 1348, 1800, 2250, 2600, 2950),
                snapshots.stream().map(MonsterSnapshot::x).toList());
        assertTrue(snapshots.stream().allMatch(monster ->
                monster.type() == 0
                        && monster.templateId() == 1
                        && monster.level() == 2
                        && monster.levelStatus() == 0
                        && monster.y() == 936
                        && monster.maxHp() == 300L
                        && monster.hp() == 300L
                        && monster.status() == 0));
    }

    @Test
    void createsFreshRuntimeObjectsForEverySeedRequest() {
        MonsterRuntimeFactory factory =
                new MonsterRuntimeFactory(resources());

        List<RuntimeMonster> first = factory.createForMap(1);
        List<RuntimeMonster> second = factory.createForMap(1);

        assertEquals(
                first.stream().map(RuntimeMonster::snapshot).toList(),
                second.stream().map(RuntimeMonster::snapshot).toList());

        for (int i = 0; i < first.size(); i++) {
            assertNotSame(first.get(i), second.get(i));
        }
    }
}
