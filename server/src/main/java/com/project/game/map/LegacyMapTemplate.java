package com.project.game.map;

import java.util.List;

public record LegacyMapTemplate(
        int id,
        int iconId,
        String name,
        int row,
        int column,
        String data,
        List<Integer> imagesBgr,
        List<List<Integer>> colorsBgr,
        boolean line,
        String dataLine,
        List<LegacyWaypoint> waypoints
) {
}
