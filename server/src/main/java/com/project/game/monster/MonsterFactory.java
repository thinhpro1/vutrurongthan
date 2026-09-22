package com.project.game.monster;

import com.project.game.resource.GameResources;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MonsterFactory {
    private final GameResources resources;

    public MonsterFactory(GameResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public List<Monster> createForMap(int mapId) {
        Map<Integer, MonsterTemplate> templatesById =
                resources.monsterTemplates().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                MonsterTemplate::id,
                                template -> template));
        return resources.monstersForMap(mapId).stream()
                .map(spawn -> {
                    MonsterTemplate template = java.util.Optional.ofNullable(
                                    templatesById.get(spawn.templateId()))
                            .orElseThrow(() -> new IllegalStateException(
                                    "missing monster template " + spawn.templateId()));
                    return new Monster(spawn, template);
                })
                .toList();
    }
}
