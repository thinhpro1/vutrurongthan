package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerSkillLoaderTest {
    @Test
    void loadsExactFreshSkillOrderByGender() {
        var skills = PlayerSkillLoader.load(Path.of("resources", "json"), true);

        assertEquals(List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36),
                skills.get(0).stream().map(skill -> skill.id()).toList());
        assertEquals(List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36),
                skills.get(1).stream().map(skill -> skill.id()).toList());
        assertEquals(List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36),
                skills.get(2).stream().map(skill -> skill.id()).toList());
    }
}
