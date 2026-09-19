package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonsterCombatLoaderTest {
    @Test
    void loadsCanonicalMonsterCombatTemplate() {
        var templates = MonsterCombatLoader.load(Path.of("resources", "json"), true);

        assertEquals(10L, templates.get(1).damage());
        assertEquals(10L, templates.get(1).potentialReward());
    }
}
