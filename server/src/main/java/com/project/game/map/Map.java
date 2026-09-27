package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.service.AreaService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Runtime của một bản đồ và các Zone thuộc về bản đồ đó. */
public final class Map {
    private final MapTemplate template;
    private final MonsterManager monsterManager;
    private final AreaService area;
    private final LinkedHashMap<Integer, Zone> zones = new LinkedHashMap<>();

    public Map(MapTemplate template, MonsterManager monsterManager, AreaService area) {
        this.template = Objects.requireNonNull(template, "template");
        this.monsterManager = Objects.requireNonNull(monsterManager, "monsterManager");
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
        return List.copyOf(zones.values());
    }

    /** Tìm waypoint thuộc Map theo luật contains của waypoint. */
    public Waypoint findWaypoint(int x, int y) {
        for (Waypoint waypoint : template.waypoints()) {
            if (waypoint.contains(x, y)) {
                return waypoint;
            }
        }
        return null;
    }

    private Zone newZone(int zoneId) {
        return new Zone(id(), zoneId, template.maxPlayer(), monsterManager.createForMap(id()), area);
    }
}
