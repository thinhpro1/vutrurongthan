package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectLoaderTest {
    @Test
    void loadsCanonicalEffectOrderAndValues() {
        var effects = EffectLoader.load(Path.of("resources", "json"), true);

        assertEquals(List.of(6, 7, 13, 17), effects.stream().map(effect -> effect.id()).toList());
        assertEquals(List.of(971, 972, 973), effects.get(2).icons());
        assertEquals(List.of(1911, 1912, 1913, 1914), effects.get(3).icons());
    }
}
