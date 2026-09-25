package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.service.AreaService;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime của một bản đồ và các Zone thuộc về bản đồ đó. */
public final class Map {
    private final MapTemplate template;
    private final MonsterFactory monsterFactory;
    private final AreaService area;
    private final ConcurrentHashMap<Integer, Zone> zones = new ConcurrentHashMap<>();

    public Map(MapTemplate template, MonsterFactory monsterFactory, AreaService area) {
        this.template = Objects.requireNonNull(template, "template");
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
        this.area = Objects.requireNonNull(area, "area");
        if (!"ONLINE".equals(template.type())) {
            throw new IllegalArgumentException("runtime map must be ONLINE: " + template.id());
        }
        for (int zoneId = 0; zoneId < template.minZone(); zoneId++) {
            zones.put(zoneId, newZone(zoneId));
        }
    }

    public int id() {
        return template.id();
    }

    public MapTemplate template() {
        return template;
    }

    /** Tìm Zone đang tồn tại mà không tạo runtime mới. */
    public Zone findZone(int zoneId) {
        return zones.get(zoneId);
    }

    /** Trả về các Zone hiện có theo thứ tự id ổn định. */
    public List<Zone> zones() {
        return zones.values().stream()
                .sorted(Comparator.comparingInt(Zone::zoneId))
                .toList();
    }

    /** Tìm waypoint thuộc Map theo luật contains của waypoint. */
    public Waypoint findWaypoint(int x, int y) {
        return template.waypoints().stream()
                .filter(waypoint -> waypoint.contains(x, y))
                .findFirst()
                .orElse(null);
    }

    private Zone newZone(int zoneId) {
        return new Zone(id(), zoneId, template.maxPlayer(), monsterFactory.createForMap(id()), area);
    }
}
