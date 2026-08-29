package com.project.game.monster;

import com.project.game.service.ResourceService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MonsterRuntimeFactory {
    private final ResourceService resources;

    public MonsterRuntimeFactory(ResourceService resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public List<RuntimeMonster> createForMap(int mapId) {
        Map<Integer, LegacyMonsterTemplate> movementById =
                resources.monsterTemplates().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                LegacyMonsterTemplate::id,
                                template -> template));
        return resources.monstersForMap(mapId).stream()
                .map(spawn -> {
                    LegacyMonsterCombatTemplate combat = resources
                            .monsterCombatTemplate(spawn.templateId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "missing monster combat template " + spawn.templateId()));
                    LegacyMonsterTemplate movement = Optional.ofNullable(
                                    movementById.get(spawn.templateId()))
                            .orElseThrow(() -> new IllegalStateException(
                                    "missing monster movement template " + spawn.templateId()));
                    return new RuntimeMonster(spawn, combat, movement);
                })
                .toList();
    }
}
