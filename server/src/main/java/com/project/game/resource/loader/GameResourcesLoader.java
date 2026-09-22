package com.project.game.resource.loader;

import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterDart;
import com.project.game.monster.MonsterSpawn;
import com.project.game.monster.MonsterTemplate;
import com.project.game.persistence.monster.MonsterRepository;
import com.project.game.resource.GameResources;
import com.project.game.resource.IconCatalog;
import com.project.game.resource.EffectImage;
import com.project.game.resource.LevelTemplate;
import com.project.game.resource.SkillTemplate;
import com.project.game.resource.FrameTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GameResourcesLoader {
    private GameResourcesLoader() {
    }

    public static GameResources fromIconRoot(Path iconRoot, int imageVersion) {
        Objects.requireNonNull(iconRoot, "iconRoot");
        IconCatalog catalog = IconCatalog.fromRoot(iconRoot);
        return new GameResources(catalog, requireLegacyImageVersion(imageVersion),
                List.of(), Map.of(), Map.of(), List.of(), List.of(), -1,
                List.of(), List.of(), Map.of());
    }

    public static GameResources fromFrameRoot(Path jsonRoot) {
        return fromFrameRoot(jsonRoot, Map.of(), -1);
    }

    public static GameResources fromFrameRoot(Path jsonRoot, int monsterVersion) {
        return fromFrameRoot(jsonRoot, Map.of(), monsterVersion);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot, Map<Integer, MapTemplate> maps) {
        return fromFrameRoot(jsonRoot, maps, -1);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot, Map<Integer, MapTemplate> maps, int monsterVersion) {
        return loadJson(Objects.requireNonNull(jsonRoot, "jsonRoot"), false,
                null, -1, monsterVersion, maps, null);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot,
            Map<Integer, MapTemplate> maps,
            int monsterVersion,
            MonsterRepository monsterRepository) {
        return loadJson(Objects.requireNonNull(jsonRoot, "jsonRoot"), false,
                null, -1, monsterVersion, maps, monsterRepository);
    }

    public static GameResources fromRoots(Path jsonRoot, Path iconRoot, int imageVersion) {
        return fromRoots(jsonRoot, iconRoot, imageVersion, -1, Map.of());
    }

    public static GameResources fromRoots(
            Path jsonRoot, Path iconRoot, int imageVersion, int monsterVersion) {
        return fromRoots(jsonRoot, iconRoot, imageVersion, monsterVersion, Map.of());
    }

    public static GameResources fromRoots(
            Path jsonRoot, Path iconRoot, int imageVersion, Map<Integer, MapTemplate> maps) {
        return fromRoots(jsonRoot, iconRoot, imageVersion, -1, maps);
    }

    public static GameResources fromRoots(
            Path jsonRoot,
            Path iconRoot,
            int imageVersion,
            int monsterVersion,
            Map<Integer, MapTemplate> maps) {
        return fromRoots(jsonRoot, iconRoot, imageVersion, monsterVersion, maps, null);
    }

    public static GameResources fromRoots(
            Path jsonRoot,
            Path iconRoot,
            int imageVersion,
            int monsterVersion,
            Map<Integer, MapTemplate> maps,
            MonsterRepository monsterRepository) {
        Objects.requireNonNull(jsonRoot, "jsonRoot");
        IconCatalog catalog = iconRoot == null ? null : IconCatalog.fromRoot(iconRoot);
        int configuredImageVersion = iconRoot == null ? -1 : requireLegacyImageVersion(imageVersion);
        return loadJson(jsonRoot, true, catalog, configuredImageVersion, monsterVersion, maps,
                monsterRepository);
    }

    private static GameResources loadJson(
            Path jsonRoot,
            boolean required,
            IconCatalog iconCatalog,
            int imageVersion,
            int monsterVersion,
            Map<Integer, MapTemplate> maps,
            MonsterRepository monsterRepository) {
        Objects.requireNonNull(maps, "maps");
        List<FrameTemplate> frames = FrameLoader.load(jsonRoot);
        Map<Integer, List<SkillTemplate>> playerSkills =
                SkillLoader.load(jsonRoot, required);
        List<LevelTemplate> levels = LevelLoader.load(jsonRoot, required);
        List<EffectImage> effects = EffectLoader.load(jsonRoot, required);
        MonsterCatalogLoader.LoadedMonsters monsters = monsterRepository == null
                ? new MonsterCatalogLoader.LoadedMonsters(-1, List.of(), List.of(), Map.of())
                : MonsterCatalogLoader.load(monsterRepository, jsonRoot, monsterVersion, maps);
        return new GameResources(
                iconCatalog,
                imageVersion,
                frames,
                playerSkills,
                maps,
                levels,
                effects,
                monsters.version(),
                monsters.darts(),
                monsters.templates(),
                monsters.spawns());
    }

    private static int requireLegacyImageVersion(int imageVersion) {
        if (imageVersion < 1 || imageVersion > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "legacy image version must be between 1 and 127: " + imageVersion);
        }
        return imageVersion;
    }
}
