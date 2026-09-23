package com.project.game.map;

import com.project.game.monster.MonsterFactory;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Single authoritative registry of runtime map zones shared by gameplay services. */
public final class ZoneRegistry {
    private record ZoneKey(int mapId, int zoneId) {
    }

    private final Map<Integer, MapTemplate> maps;
    private final MonsterFactory monsterFactory;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();

    public ZoneRegistry(Map<Integer, MapTemplate> maps, MonsterFactory monsterFactory) {
        Objects.requireNonNull(maps, "maps");
        TreeMap<Integer, MapTemplate> copiedMaps = new TreeMap<>();
        maps.forEach((mapId, map) -> {
            if (mapId == null || map == null) {
                throw new NullPointerException("maps must not contain null entries");
            }
            if (mapId != map.id()) {
                throw new IllegalArgumentException("map catalog key does not match map id");
            }
            copiedMaps.put(mapId, map);
        });
        this.maps = Collections.unmodifiableMap(copiedMaps);
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
        for (MapTemplate map : this.maps.values()) {
            if ("ONLINE".equals(map.type())) {
                for (int zoneId = 0; zoneId < map.minZone(); zoneId++) {
                    zones.put(new ZoneKey(map.id(), zoneId), create(map, zoneId));
                }
            }
        }
    }

    /** Returns an existing zone without creating one. */
    public Zone find(int mapId, int zoneId) {
        return zones.get(new ZoneKey(mapId, zoneId));
    }

    /** Returns the policy-valid authoritative zone, creating it atomically when first requested. */
    public Zone getOrCreate(int mapId, int zoneId) {
        MapTemplate map = requireOnlineMap(mapId, zoneId);
        ZoneKey key = new ZoneKey(mapId, zoneId);
        return zones.computeIfAbsent(key, ignored -> create(map, zoneId));
    }

    /** Returns a snapshot of currently registered zones; empty zones remain registered. */
    public List<Zone> snapshot() {
        return zones.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparingInt(ZoneKey::mapId)
                                .thenComparingInt(ZoneKey::zoneId)))
                .map(Map.Entry::getValue)
                .toList();
    }

    private MapTemplate requireOnlineMap(int mapId, int zoneId) {
        MapTemplate map = maps.get(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown map " + mapId);
        }
        if (!"ONLINE".equals(map.type())) {
            throw new IllegalArgumentException("map " + mapId + " is not ONLINE");
        }
        if (zoneId < 0 || zoneId >= map.maxZone()) {
            throw new IllegalArgumentException(
                    "zone " + zoneId + " is outside map " + mapId + " bound 0.."
                            + (map.maxZone() - 1));
        }
        return map;
    }

    private Zone create(MapTemplate map, int zoneId) {
        return new Zone(map.id(), zoneId, map.maxPlayer(), monsterFactory.createForMap(map.id()));
    }
}
