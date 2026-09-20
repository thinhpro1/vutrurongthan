package com.project.game.resource;

import com.project.game.map.MapTemplate;
import com.project.game.monster.LegacyMonsterCombatTemplate;
import com.project.game.monster.LegacyMonsterDart;
import com.project.game.monster.LegacyMonsterSpawn;
import com.project.game.monster.LegacyMonsterTemplate;
import com.project.game.resource.loader.GameResourcesLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable catalog of already-loaded legacy resource data. */
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
            Map.of(),
            Map.of());

    private final IconResourceCatalog iconCatalog;
    private final int imageVersion;
    private final List<FrameTemplate> frames;
    private final Map<Integer, List<LegacyPlayerSkill>> playerSkills;
    private final Map<Integer, MapTemplate> maps;
    private final List<LegacyLevel> levels;
    private final List<LegacyEffectImage> effects;
    private final int monsterVersion;
    private final List<LegacyMonsterDart> monsterDarts;
    private final List<LegacyMonsterTemplate> monsterTemplates;
    private final Map<Integer, List<LegacyMonsterSpawn>> monsterSpawns;
    private final Map<Integer, LegacyMonsterCombatTemplate> monsterCombatTemplates;

    public GameResources(
            IconResourceCatalog iconCatalog,
            int imageVersion,
            List<FrameTemplate> frames,
            Map<Integer, List<LegacyPlayerSkill>> playerSkills,
            Map<Integer, MapTemplate> maps,
            List<LegacyLevel> levels,
            List<LegacyEffectImage> effects,
            int monsterVersion,
            List<LegacyMonsterDart> monsterDarts,
            List<LegacyMonsterTemplate> monsterTemplates,
            Map<Integer, List<LegacyMonsterSpawn>> monsterSpawns,
            Map<Integer, LegacyMonsterCombatTemplate> monsterCombatTemplates) {
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
        this.monsterCombatTemplates = Map.copyOf(
                Objects.requireNonNull(monsterCombatTemplates, "monsterCombatTemplates"));
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

    public static GameResources fromRoots(Path iconRoot, Path jsonRoot) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, iconRoot == null ? -1 : 1);
    }

    public static GameResources fromRoots(Path iconRoot, Path jsonRoot, int imageVersion) {
        return GameResourcesLoader.fromRoots(jsonRoot, iconRoot, imageVersion);
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

    public List<LegacyPlayerSkill> playerSkills(int gender) {
        return playerSkills.getOrDefault(gender, List.of());
    }

    public Optional<MapTemplate> map(int mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public List<LegacyLevel> levels() {
        return levels;
    }

    public List<LegacyEffectImage> effects() {
        return effects;
    }

    public int monsterVersion() {
        return monsterVersion;
    }

    public List<LegacyMonsterDart> monsterDarts() {
        return monsterDarts;
    }

    public List<LegacyMonsterTemplate> monsterTemplates() {
        return monsterTemplates;
    }

    public List<LegacyMonsterSpawn> monstersForMap(int mapId) {
        return monsterSpawns.getOrDefault(mapId, List.of());
    }

    public Optional<LegacyMonsterCombatTemplate> monsterCombatTemplate(int templateId) {
        return Optional.ofNullable(monsterCombatTemplates.get(templateId));
    }

    private static <T> Map<Integer, List<T>> copyLists(Map<Integer, List<T>> source) {
        return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(Objects.requireNonNull(entry.getValue(), "resource list"))));
    }
}
