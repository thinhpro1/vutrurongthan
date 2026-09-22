package com.project.game.monster;

import com.project.game.resource.GameResources;
import com.project.game.persistence.monster.MonsterRepository;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
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
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                MonsterTestSupport.canonicalRepository());
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
                List.of(101, 102, 103, 104, 105, 106),
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
    void missingCanonicalTemplateFailsClearly() {
        MonsterRepository repository = new MonsterRepository() {
            @Override
            public List<TemplateRow> findAllTemplates() {
                return List.of(new TemplateRow(
                        1, "Hổ nanh kiếm", 2, 300L, 10L, 10L,
                        100, 1, 1, 0,
                        "[11818,11819,11820,11821,11822]", "[11823]", "[11824]",
                        175, 95));
            }

            @Override
            public List<SpawnRow> findAllSpawns() {
                return List.of(new SpawnRow(101, 1, 2, 975, 936));
            }
        };

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> GameResources.fromFrameRoot(
                        Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2, repository));
        assertTrue(failure.getMessage().contains("missing template 2"));
    }
}
