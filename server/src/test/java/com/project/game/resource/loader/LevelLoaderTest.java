package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelLoaderTest {
    @Test
    void loadsCanonicalLevelTable() {
        var levels = LevelLoader.load(Path.of("resources", "json"), true);

        assertEquals(102, levels.size());
        assertEquals(0, levels.get(0).id());
        assertEquals(101, levels.get(101).id());
    }
}
