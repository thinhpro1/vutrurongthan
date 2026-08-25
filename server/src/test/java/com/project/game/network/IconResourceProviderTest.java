package com.project.game.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconResourceProviderTest {
    @Test
    void loadsOnlyNumericPngFilesBelowConfiguredRoot(@TempDir Path root) throws IOException {
        byte[] expected = new byte[]{1, 2, 3, 4};
        Files.write(root.resolve("5.png"), expected);
        FileSystemIconResourceProvider provider = new FileSystemIconResourceProvider(root);

        assertArrayEquals(expected, provider.load(5).orElseThrow());
        assertTrue(provider.load(6).isEmpty());
    }

    @Test
    void absentRootReportsMissingWithoutFabricatingBytes(@TempDir Path root) {
        FileSystemIconResourceProvider provider = new FileSystemIconResourceProvider(
                root.resolve("does-not-exist"));

        assertTrue(provider.load(5).isEmpty());
    }
}
