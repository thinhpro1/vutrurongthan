package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameLoaderTest {
    @Test
    void loadsApprovedFrameOrder() {
        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                FrameLoader.load(Path.of("resources", "json")).stream().map(frame -> frame.id()).toList());
    }

    @Test
    void missingFrameRootIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameLoader.load(Path.of("resources", "missing")));
    }
}
