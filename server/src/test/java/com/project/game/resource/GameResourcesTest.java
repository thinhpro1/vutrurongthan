package com.project.game.resource;

import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameResourcesTest {
    @Test
    void unavailableServiceHasNoMonsterResources() {
        GameResources resources = GameResources.unavailable();

        assertEquals(-1, resources.monsterVersion());
        assertTrue(resources.monsterDarts().isEmpty());
        assertTrue(resources.monsterTemplates().isEmpty());
        assertTrue(resources.monstersForMap(0).isEmpty());
        assertTrue(resources.monstersForMap(1).isEmpty());
    }

    @Test
    void loadsOnlyNumericPngFilesBelowConfiguredRoot(@TempDir Path root) throws IOException {
        byte[] expected = new byte[]{1, 2, 3, 4};
        Files.write(root.resolve("5.png"), expected);
        GameResources resources = GameResources.fromIconRoot(root);

        assertArrayEquals(expected, resources.loadIcon(5).orElseThrow());
        assertTrue(resources.loadIcon(6).isEmpty());
    }

    @Test
    void exposesSortedIconManifestAndCatalogBytes(@TempDir Path root) throws IOException {
        Files.write(root.resolve("10.png"), new byte[]{1, 2, 3});
        Files.write(root.resolve("2.png"), new byte[]{4, 5, 6});

        GameResources resources = GameResources.fromIconRoot(root, 2);

        assertEquals(List.of(2, 10), resources.iconManifest().stream()
                .map(IconFingerprint::iconId)
                .toList());
        assertArrayEquals(new byte[]{4, 5, 6}, resources.loadIcon(2).orElseThrow());
    }

    @Test
    void unavailableServiceHasEmptyIconManifest() {
        assertTrue(GameResources.unavailable().iconManifest().isEmpty());
    }

    @Test
    void iconRootExposesConfiguredImageVersion(@TempDir Path root) {
        GameResources resources = GameResources.fromIconRoot(root, 7);

        assertEquals(7, resources.imageVersion());
    }

    @Test
    void unavailableImageResourcesExposeMinusOneVersion() {
        assertEquals(-1, GameResources.unavailable().imageVersion());
    }

    @Test
    void iconRootRejectsInvalidLegacyImageVersions(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> GameResources.fromIconRoot(root, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GameResources.fromIconRoot(root, -1));
        assertThrows(IllegalArgumentException.class,
                () -> GameResources.fromIconRoot(root, 128));
    }

    @Test
    void absentRootReportsMissingWithoutFabricatingBytes(@TempDir Path root) {
        GameResources resources = GameResources.fromIconRoot(root.resolve("does-not-exist"));

        assertTrue(resources.loadIcon(5).isEmpty());
    }

    @Test
    void unavailableServiceReportsMissing() {
        assertTrue(GameResources.unavailable().loadIcon(5).isEmpty());
    }

    @Test
    void fromFrameRootLoadsRequiredFramesAndLeavesMissingOptionalFamiliesEmpty(
            @TempDir Path root) throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));

        GameResources resources = GameResources.fromFrameRoot(root);

        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                resources.frames().stream().map(FrameTemplate::id).toList());
        assertTrue(resources.playerSkills(0).isEmpty());
        assertTrue(resources.map(0).isEmpty());
        assertTrue(resources.levels().isEmpty());
        assertTrue(resources.effects().isEmpty());
        assertEquals(-1, resources.monsterVersion());
        assertTrue(resources.monsterDarts().isEmpty());
        assertTrue(resources.monsterTemplates().isEmpty());
    }

    @Test
    void fromFrameRootAcceptsExplicitMonsterResourceVersion() {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                MonsterTestSupport.canonicalRepository());

        assertEquals(2, resources.monsterVersion());
        assertEquals(1, resources.monsterTemplates().size());
        assertEquals(List.of(11824), resources.monsterTemplates().getFirst().iconsInjure());
        assertEquals(List.of(11823), resources.monsterTemplates().getFirst().iconsAttack());
        assertEquals(List.of(101, 102, 103, 104, 105, 106),
                resources.monstersForMap(1).stream().map(com.project.game.monster.MonsterSpawn::id).toList());
    }

    @Test
    void fromFrameRootDoesNotInspectLegacyMapBootstrap(@TempDir Path root) throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));
        Files.writeString(root.resolve("MapBootstrap.json"), "not-json");

        GameResources resources = GameResources.fromFrameRoot(root);

        assertTrue(resources.map(0).isEmpty());
    }

    @Test
    void fromRootsRequiresAllSupportedBootstraps(@TempDir Path root) throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));

        assertThrows(IllegalArgumentException.class,
                () -> GameResources.fromRoots(null, root));
    }
}
