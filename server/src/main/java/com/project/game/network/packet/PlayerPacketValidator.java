package com.project.game.network.packet;

import com.project.game.player.Appearance;
import com.project.game.player.Player;

/** Kiểm tra giá trị trước khi thu hẹp vào field signed trên wire legacy. */
public final class PlayerPacketValidator {
    private PlayerPacketValidator() {
    }

    public static void validateAddPlayer(Player player) {
        validatePlayerIdentityAndAppearance(player);
        requireSignedShort(player.x(), "player.x");
        requireSignedShort(player.y(), "player.y");
    }

    public static void validatePlayerInfo(Player player) {
        validatePlayerIdentityAndAppearance(player);
    }

    public static void validateMapInfo(int zoneId, int x, int y, int mapId) {
        requireNonNegativeSignedShort(mapId, "mapId");
        requireSignedByte(zoneId, "zoneId");
        requireSignedShort(x, "x");
        requireSignedShort(y, "y");
    }

    public static void validatePosition(int x, int y) {
        requireSignedShort(x, "x");
        requireSignedShort(y, "y");
    }

    private static void validatePlayerIdentityAndAppearance(Player player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        requireNonNegativeSignedShort(player.level(), "level");
        requireSignedByte(player.appearance().spaceship(), "spaceship");
        requirePositiveSignedByte(player.currentStats().speed(), "speed");
        validateAppearance(player.appearance());
    }

    private static void validateAppearance(Appearance appearance) {
        requireSignedShort(appearance.head(), "head");
        requireSignedShort(appearance.body(), "body");
        requireSignedShort(appearance.mount(), "mount");
        requireSignedShort(appearance.bag(), "bag");
        requireSignedShort(appearance.medal(), "medal");
        requireSignedShort(appearance.aura(), "aura");
    }

    private static void requireNonNegativeSignedShort(int value, String field) {
        if (value < 0 || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must fit non-negative signed short");
        }
    }

    private static void requireSignedShort(int value, String field) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must fit signed short");
        }
    }

    private static void requireSignedByte(int value, String field) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must fit signed byte");
        }
    }

    private static void requirePositiveSignedByte(int value, String field) {
        if (value < 1 || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must fit positive signed byte");
        }
    }
}
