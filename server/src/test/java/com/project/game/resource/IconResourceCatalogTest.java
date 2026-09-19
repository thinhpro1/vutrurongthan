package com.project.game.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconResourceCatalogTest {
    @Test
    void buildsSortedPerIconManifest(@TempDir Path root) throws Exception {
        Files.write(root.resolve("10.png"), new byte[]{1, 2, 3});
        Files.write(root.resolve("2.png"), new byte[]{4, 5, 6});
        Files.write(root.resolve("005.png"), new byte[]{9});
        Files.write(root.resolve("note.txt"), new byte[]{9});

        IconResourceCatalog catalog = IconResourceCatalog.fromRoot(root);

        assertEquals(List.of(2, 10), catalog.manifest().stream()
                .map(IconFingerprint::iconId)
                .toList());
    }

    @Test
    void acceptsOnlyCanonicalNumericPngNames(@TempDir Path root) throws Exception {
        Files.write(root.resolve("0.png"), new byte[]{0});
        Files.write(root.resolve("32767.png"), new byte[]{1});
        Files.write(root.resolve("005.png"), new byte[]{2});
        Files.write(root.resolve("-1.png"), new byte[]{3});
        Files.write(root.resolve("32768.png"), new byte[]{4});
        Files.write(root.resolve("abc.png"), new byte[]{5});
        Files.write(root.resolve("5.jpg"), new byte[]{6});

        IconResourceCatalog catalog = IconResourceCatalog.fromRoot(root);

        assertEquals(List.of(0, 32767), catalog.manifest().stream()
                .map(IconFingerprint::iconId)
                .toList());
    }

    @Test
    void changingOneIconChangesOnlyItsFingerprint(@TempDir Path root) throws Exception {
        Files.write(root.resolve("2.png"), new byte[]{1});
        Files.write(root.resolve("10.png"), new byte[]{2});

        IconResourceCatalog first = IconResourceCatalog.fromRoot(root);
        long first2 = fingerprint(first, 2);
        long first10 = fingerprint(first, 10);

        Files.write(root.resolve("10.png"), new byte[]{3});

        IconResourceCatalog second = IconResourceCatalog.fromRoot(root);

        assertEquals(first2, fingerprint(second, 2));
        assertNotEquals(first10, fingerprint(second, 10));
    }

    @Test
    void unknownIconReturnsEmpty(@TempDir Path root) throws Exception {
        Files.write(root.resolve("5.png"), new byte[]{1, 2, 3});

        assertTrue(IconResourceCatalog.fromRoot(root).loadIcon(6).isEmpty());
    }

    @Test
    void lazyLoadKeepsCachedBytesAfterBackingFileIsDeleted(@TempDir Path root) throws Exception {
        byte[] expected = new byte[]{1, 2, 3, 4};
        Path icon = root.resolve("5.png");
        Files.write(icon, expected);
        IconResourceCatalog catalog = IconResourceCatalog.fromRoot(root);

        assertArrayEquals(expected, catalog.loadIcon(5).orElseThrow());
        Files.delete(icon);

        assertArrayEquals(expected, catalog.loadIcon(5).orElseThrow());
    }

    @Test
    void mutationBeforeFirstLoadIsRejected(@TempDir Path root) throws Exception {
        Path icon = root.resolve("5.png");
        Files.write(icon, new byte[]{1, 2, 3});
        IconResourceCatalog catalog = IconResourceCatalog.fromRoot(root);
        Files.write(icon, new byte[]{4, 5, 6});

        assertTrue(catalog.loadIcon(5).isEmpty());
    }

    @Test
    void absentRootProducesEmptyCatalog(@TempDir Path root) {
        IconResourceCatalog catalog = IconResourceCatalog.fromRoot(root.resolve("missing"));

        assertTrue(catalog.manifest().isEmpty());
        assertTrue(catalog.loadIcon(5).isEmpty());
    }

    private static long fingerprint(IconResourceCatalog catalog, int iconId) {
        return catalog.manifest().stream()
                .filter(icon -> icon.iconId() == iconId)
                .findFirst()
                .orElseThrow()
                .fingerprint();
    }
}
