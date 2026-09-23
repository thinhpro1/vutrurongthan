package com.project.game.resource;

import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterDart;
import com.project.game.monster.MonsterSpawn;
import com.project.game.monster.MonsterTemplate;
import com.project.game.persistence.monster.MonsterRepository;
import com.project.game.resource.loader.GameResourcesLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable catalog of already-loaded static game resources. */
public final class GameResources {
    private static final GameResources UNAVAILABLE = new GameResources(
            null,
            -1,
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            -1,
            List.of(),
            List.of(),
            Map.of());

    private final IconCatalog iconCatalog;
    private final int imageVersion;
    private final List<FrameTemplate> frames;
    private final Map<Integer, List<SkillTemplate>> playerSkills;
    private final Map<Integer, MapTemplate> maps;
    private final List<LevelTemplate> levels;
    private final List<EffectImage> effects;
    private final int monsterVersion;
    private final List<MonsterDart> monsterDarts;
    private final List<MonsterTemplate> monsterTemplates;
    private final Map<Integer, List<MonsterSpawn>> monsterSpawns;

    public GameResources(
            IconCatalog iconCatalog,
            int imageVersion,
            List<FrameTemplate> frames,
            Map<Integer, List<SkillTemplate>> playerSkills,
            Map<Integer, MapTemplate> maps,
            List<LevelTemplate> levels,
            List<EffectImage> effects,
            int monsterVersion,
            List<MonsterDart> monsterDarts,
            List<MonsterTemplate> monsterTemplates,
            Map<Integer, List<MonsterSpawn>> monsterSpawns) {
        this.iconCatalog = iconCatalog;
        this.imageVersion = imageVersion;
        this.frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        this.playerSkills = copyLists(Objects.requireNonNull(playerSkills, "playerSkills"));
        this.maps = Map.copyOf(Objects.requireNonNull(maps, "maps"));
        this.levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
        this.effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        this.monsterVersion = monsterVersion;
        this.monsterDarts = List.copyOf(Objects.requireNonNull(monsterDarts, "monsterDarts"));
        this.monsterTemplates = List.copyOf(Objects.requireNonNull(monsterTemplates, "monsterTemplates"));
        this.monsterSpawns = copyLists(Objects.requireNonNull(monsterSpawns, "monsterSpawns"));
    }

    public static GameResources unavailable() {
        return UNAVAILABLE;
    }

    public static GameResources fromIconRoot(Path iconRoot) {
        return fromIconRoot(iconRoot, 1);
    }

    public static GameResources fromIconRoot(Path iconRoot, int imageVersion) {
        return GameResourcesLoader.fromIconRoot(
                Objects.requireNonNull(iconRoot, "iconRoot"), imageVersion);
    }

    public static GameResources fromFrameRoot(Path jsonRoot) {
        return GameResourcesLoader.fromFrameRoot(
                Objects.requireNonNull(jsonRoot, "jsonRoot"));
    }

    public static GameResources fromFrameRoot(Path jsonRoot, int monsterVersion) {
        return GameResourcesLoader.fromFrameRoot(
                Objects.requireNonNull(jsonRoot, "jsonRoot"), monsterVersion);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot, Map<Integer, MapTemplate> maps) {
        return GameResourcesLoader.fromFrameRoot(
                Objects.requireNonNull(jsonRoot, "jsonRoot"), maps);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot, Map<Integer, MapTemplate> maps, int monsterVersion) {
        return GameResourcesLoader.fromFrameRoot(
                Objects.requireNonNull(jsonRoot, "jsonRoot"), maps, monsterVersion);
    }

    public static GameResources fromFrameRoot(
            Path jsonRoot,
            Map<Integer, MapTemplate> maps,
            int monsterVersion,
            MonsterRepository monsterRepository) {
        return GameResourcesLoader.fromFrameRoot(
                Objects.requireNonNull(jsonRoot, "jsonRoot"), maps, monsterVersion,
                monsterRepository);
    }

    public static GameResources fromRoots(Path iconRoot, Path jsonRoot) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, iconRoot == null ? -1 : 1);
    }

    public static GameResources fromRoots(Path iconRoot, Path jsonRoot, int imageVersion) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, imageVersion);
    }

    public static GameResources fromRoots(
            Path iconRoot, Path jsonRoot, int imageVersion, int monsterVersion) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, imageVersion, monsterVersion);
    }

    public static GameResources fromRoots(
            Path iconRoot, Path jsonRoot, int imageVersion, Map<Integer, MapTemplate> maps) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, imageVersion, maps);
    }

    public static GameResources fromRoots(
            Path iconRoot,
            Path jsonRoot,
            int imageVersion,
            int monsterVersion,
            Map<Integer, MapTemplate> maps) {
        return GameResourcesLoader.fromRoots(
                jsonRoot, iconRoot, imageVersion, monsterVersion, maps);
    }

    public static GameResources fromRoots(
            Path iconRoot,
            Path jsonRoot,
            int imageVersion,
            int monsterVersion,
            Map<Integer, MapTemplate> maps,
            MonsterRepository monsterRepository) {
        return GameResourcesLoader.fromRoots(
                jsonRoot, iconRoot, imageVersion, monsterVersion, maps, monsterRepository);
    }

    public int imageVersion() {
        return imageVersion;
    }

    public List<IconFingerprint> iconManifest() {
        return iconCatalog == null ? List.of() : iconCatalog.manifest();
    }

    public Optional<byte[]> loadIcon(int iconId) {
        return iconCatalog == null ? Optional.empty() : iconCatalog.loadIcon(iconId);
    }

    public List<FrameTemplate> frames() {
        return frames;
    }

    public List<SkillTemplate> playerSkills(int gender) {
        return playerSkills.getOrDefault(gender, List.of());
    }

    public Optional<MapTemplate> map(int mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public Map<Integer, MapTemplate> maps() {
        return maps;
    }

    public List<LevelTemplate> levels() {
        return levels;
    }

    public List<EffectImage> effects() {
        return effects;
    }

    public int monsterVersion() {
        return monsterVersion;
    }

    public List<MonsterDart> monsterDarts() {
        return monsterDarts;
    }

    public List<MonsterTemplate> monsterTemplates() {
        return monsterTemplates;
    }

    public List<MonsterSpawn> monstersForMap(int mapId) {
        return monsterSpawns.getOrDefault(mapId, List.of());
    }

    private static <T> Map<Integer, List<T>> copyLists(Map<Integer, List<T>> source) {
        return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(Objects.requireNonNull(entry.getValue(), "resource list"))));
    }
}
