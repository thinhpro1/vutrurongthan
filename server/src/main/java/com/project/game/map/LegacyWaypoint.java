package com.project.game.map;

/** A canonical legacy map transition point and its destination spawn. */
public record LegacyWaypoint(
        int id,
        int goMap,
        int x,
        int y,
        int goX,
        int goY,
        int type
) {
    public boolean contains(int playerX, int playerY) {
        return switch (type) {
            case 0 -> playerX >= x
                    && playerX <= x + 50
                    && playerY >= y - 200
                    && playerY <= y;
            case 1 -> playerX >= x - 50
                    && playerX <= x
                    && playerY >= y - 200
                    && playerY <= y;
            case 2 -> playerX >= x - 200
                    && playerX <= x + 200
                    && playerY >= y - 200
                    && playerY <= y;
            default -> false;
        };
    }
}
