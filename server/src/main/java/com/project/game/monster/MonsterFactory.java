package com.project.game.monster;

import com.project.game.resource.GameResources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates independent Monster runtime state from immutable resource data. */
public final class MonsterFactory {
    private final GameResources resources;
    private final Map<Integer, MonsterTemplate> templates;

    public MonsterFactory(GameResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
        Map<Integer, MonsterTemplate> index = new HashMap<>();
        for (MonsterTemplate template : resources.monsterTemplates()) {
            if (index.putIfAbsent(template.id(), template) != null) {
                throw new IllegalArgumentException("duplicate monster template " + template.id());
            }
        }
        templates = Map.copyOf(index);
    }

    public List<Monster> createForMap(int mapId) {
        List<MonsterSpawn> spawns = resources.monstersForMap(mapId);
        List<Monster> monsters = new ArrayList<>(spawns.size());
        for (MonsterSpawn spawn : spawns) {
            MonsterTemplate template = templates.get(spawn.templateId());
            if (template == null) {
                throw new IllegalStateException("missing monster template " + spawn.templateId());
            }
            monsters.add(new Monster(spawn, template));
        }
        return List.copyOf(monsters);
    }
}
