package com.project.game.map;

import java.util.List;
import java.util.Objects;

public record MapTemplate(
        int id,
        String name,
        String type,
        String planet,
        int minZone,
        int maxZone,
        int maxPlayer,
        int dataId,
        MapData data,
        List<Waypoint> waypoints
) {
    public MapTemplate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(planet, "planet");
        Objects.requireNonNull(data, "data");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
    }
}
