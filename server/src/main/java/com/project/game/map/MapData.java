package com.project.game.map;

import java.util.List;
import java.util.Objects;

/** Canonical static map data loaded from resources/maps/{data}.json. */
public record MapData(
        int id,
        int terrain,
        int row,
        int column,
        Background background,
        Collision collision
) {
    public static final int TILE_SIZE = 72;

    public MapData {
        background = Objects.requireNonNull(background, "background");
        collision = Objects.requireNonNull(collision, "collision");
    }

    public int width() {
        return Math.multiplyExact(column, TILE_SIZE);
    }

    public int height() {
        return Math.multiplyExact(row, TILE_SIZE);
    }

    public record Background(
            List<Integer> skyColor,
            List<Layer> layers
    ) {
        public Background {
            skyColor = List.copyOf(Objects.requireNonNull(skyColor, "skyColor"));
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        }
    }

    public record Layer(
            int image,
            List<Integer> fillColor
    ) {
        public Layer {
            fillColor = List.copyOf(Objects.requireNonNull(fillColor, "fillColor"));
        }
    }

    public record Collision(
            CollisionType type,
            String data,
            List<Line> lines
    ) {
        public Collision {
            type = Objects.requireNonNull(type, "type");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    public enum CollisionType {
        GRID,
        LINE
    }

    public record Line(
            LineType type,
            List<Point> points
    ) {
        public Line {
            type = Objects.requireNonNull(type, "type");
            points = List.copyOf(Objects.requireNonNull(points, "points"));
        }
    }

    public enum LineType {
        BLOCK,
        PLATFORM
    }

    public record Point(int x, int y) {
    }
}
