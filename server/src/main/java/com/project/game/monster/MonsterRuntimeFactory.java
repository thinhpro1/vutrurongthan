package com.project.game.monster;

import com.project.game.service.ResourceService;

import java.util.List;
import java.util.Objects;

public final class MonsterRuntimeFactory {
    private final ResourceService resources;

    public MonsterRuntimeFactory(ResourceService resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public List<RuntimeMonster> createForMap(int mapId) {
        return resources.monstersForMap(mapId).stream()
                .map(RuntimeMonster::new)
                .toList();
    }
}
