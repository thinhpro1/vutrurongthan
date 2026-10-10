package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.service.AreaService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Một bản đồ: template và các khu vực (Zone) của nó. */
public final class Map {
    private final MapTemplate template;
    private final MonsterManager monsterManager;
    private final AreaService area;
    private final LinkedHashMap<Integer, Zone> zones = new LinkedHashMap<>();
    private MapManager manager; // null nếu Map đứng riêng (test)

    public Map(MapTemplate template, MonsterManager monsterManager, AreaService area) {
        this.template = Objects.requireNonNull(template, "template");
        this.monsterManager = Objects.requireNonNull(monsterManager, "monsterManager");
        this.area = Objects.requireNonNull(area, "area");
        if (!"ONLINE".equals(template.type())) {
            throw new IllegalArgumentException("runtime map must be ONLINE: " + template.id());
        }
        for (int zoneId = 0; zoneId < template.minZone(); zoneId++) {
            zones.put(zoneId, new Zone(this, zoneId, monsterManager.createForMap(id()), area));
        }
    }

    public int id() {
        return template.id();
    }

    public MapTemplate template() {
        return template;
    }

    /** MapManager đang giữ Map này (để Player đi sang map khác). */
    public MapManager manager() {
        return manager;
    }

    void setManager(MapManager manager) {
        this.manager = manager;
    }

    /** Tìm Zone đang tồn tại mà không tạo Zone mới. */
    public Zone findZone(int zoneId) {
        return zones.get(zoneId);
    }

    /** Các Zone theo thứ tự id. */
    public List<Zone> zones() {
        return List.copyOf(zones.values());
    }

    /**
     * Chọn Zone cho người mới tới (giống findOrRandomZone(-1) của rongthan): Zone đầu tiên còn
     * chỗ, để người đi cùng nhau vào chung khu. Tất cả đều đầy thì vẫn vào Zone ít người nhất,
     * không để người chơi kẹt lại.
     */
    public Zone findOrRandomZone() {
        for (Zone zone : zones.values()) {
            if (zone.playerCount() < zone.maxPlayer()) {
                return zone;
            }
        }
        Zone leastCrowded = null;
        for (Zone zone : zones.values()) {
            if (leastCrowded == null || zone.playerCount() < leastCrowded.playerCount()) {
                leastCrowded = zone;
            }
        }
        return leastCrowded;
    }

    /** Waypoint mà Player đang đứng lên, hoặc null. */
    public Waypoint findWaypoint(int x, int y) {
        for (Waypoint waypoint : template.waypoints()) {
            if (waypoint.contains(x, y)) {
                return waypoint;
            }
        }
        return null;
    }

    /** Tên map đích của waypoint, hiển thị trong MAP_INFO. */
    public String waypointName(Waypoint waypoint) {
        if (manager == null) {
            return "";
        }
        MapTemplate target = manager.findTemplate(waypoint.goMap());
        if (target == null) {
            return "";
        }
        return target.name();
    }
}
