package com.project.game.map;

import com.project.game.monster.MonsterRuntimeFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Single authoritative registry of runtime map zones shared by gameplay services. */
public final class ZoneRegistry {
    private record ZoneKey(int mapId, int zoneId) {
    }

    private final MonsterRuntimeFactory monsterFactory;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();

    public ZoneRegistry(MonsterRuntimeFactory monsterFactory) {
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
    }

    /** Returns an existing zone without creating one. */
    public Zone find(int mapId, int zoneId) {
        return zones.get(new ZoneKey(mapId, zoneId));
    }

    /** Returns the authoritative zone, creating it atomically when first requested. */
    public Zone getOrCreate(int mapId, int zoneId) {
        ZoneKey key = new ZoneKey(mapId, zoneId);
        return zones.computeIfAbsent(key,
                ignored -> new Zone(mapId, zoneId, monsterFactory.createForMap(mapId)));
    }

    /** Returns a snapshot of currently registered zones; empty zones remain registered. */
    public List<Zone> snapshot() {
        return List.copyOf(zones.values());
    }
}
