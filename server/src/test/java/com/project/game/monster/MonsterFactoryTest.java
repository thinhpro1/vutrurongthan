package com.project.game.monster;

import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterFactoryTest {
    private static GameResources resources() {
        return GameResources.fromFrameRoot(
                Path.of("resources", "json"), 2);
    }

    @Test
    void createsCanonicalMonstersForSupportedMaps() {
        MonsterFactory factory =
                new MonsterFactory(resources());

        assertTrue(factory.createForMap(0).isEmpty());

        List<Monster> map1 = factory.createForMap(1);
        assertEquals(6, map1.size());

        List<MonsterSnapshot> snapshots =
                map1.stream().map(Monster::snapshot).toList();

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
        assertTrue(map1.stream().allMatch(monster -> monster.damage() == 10L));
        assertTrue(map1.stream().allMatch(monster -> monster.rangeMove() == 100));
        assertTrue(map1.stream().allMatch(monster -> monster.speed() == 1));
        assertTrue(map1.stream().allMatch(monster -> monster.moveType() == 1));
        assertTrue(map1.stream().allMatch(monster -> monster.moveDir() == 1));
    }

    @Test
    void createsFreshRuntimeObjectsForEverySeedRequest() {
        MonsterFactory factory =
                new MonsterFactory(resources());

        List<Monster> first = factory.createForMap(1);
        List<Monster> second = factory.createForMap(1);

        assertEquals(
                first.stream().map(Monster::snapshot).toList(),
                second.stream().map(Monster::snapshot).toList());

        for (int i = 0; i < first.size(); i++) {
            assertNotSame(first.get(i), second.get(i));
        }
    }

    @Test
    void missingCombatStatFailsClearly(@TempDir Path root) throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));
        Files.copy(Path.of("resources", "json", "MonsterDartTemplate.json"),
                root.resolve("MonsterDartTemplate.json"));
        Files.copy(Path.of("resources", "json", "MonsterBootstrap.json"),
                root.resolve("MonsterBootstrap.json"));

        GameResources resources = GameResources.fromFrameRoot(root, 2);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new MonsterFactory(resources).createForMap(1));
        assertTrue(failure.getMessage().contains("missing monster combat template 1"));
    }
}
