package com.project.game.persistence.map;

import java.util.List;

public interface MapRepository {
    List<MapRow> findAllMaps();

    List<WaypointRow> findAllWaypoints();

    record MapRow(
            int id,
            String name,
            String type,
            String planet,
            int minZone,
            int maxZone,
            int maxPlayer,
            int data,
            boolean enabled
    ) {}

    record WaypointRow(
            long id,
            int mapId,
            int x,
            int y,
            int type,
            int goMap,
            int goX,
            int goY
    ) {}
}
