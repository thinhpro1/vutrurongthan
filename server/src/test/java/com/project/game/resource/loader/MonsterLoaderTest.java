package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonsterLoaderTest {
    @Test
    void loadsCanonicalMonsterBootstrap() {
        var monsters = MonsterLoader.load(Path.of("resources", "json"), true);

        assertEquals(1, monsters.version());
        assertEquals(1, monsters.darts().size());
        assertEquals(1, monsters.templates().size());
        assertEquals(0, monsters.spawns().get(0).size());
        assertEquals(6, monsters.spawns().get(1).size());
    }
}
