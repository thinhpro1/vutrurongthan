package com.project.game.network.packet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.project.game.map.MapData;
import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.Player;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Serializes the legacy MAP_INFO payload without owning map/session state. */
public final class MapPacketWriter {
    private static final Gson GSON = new Gson();

    public Message mapInfo(
            Player player,
            MapTemplate map,
            boolean includeTemplate,
            List<String> waypointTargetNames,
            List<MonsterSnapshot> monsters) throws IOException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(waypointTargetNames, "waypointTargetNames");
        Objects.requireNonNull(monsters, "monsters");
        if (waypointTargetNames.size() != map.waypoints().size()) {
            throw new IllegalArgumentException(
                    "waypoint target-name count must match map waypoints");
        }
        if (map.waypoints().size() > Byte.MAX_VALUE) {
            throw new IOException("too many waypoints for map " + map.id());
        }
        if (monsters.size() > Byte.MAX_VALUE) {
            throw new IOException("too many monsters for map " + map.id());
        }
        PlayerPacketValidator.validateMapInfo(player, map.id());

        MessageWriter writer = new MessageWriter().writeShort(map.id());
        if (includeTemplate) {
            writeTemplate(writer, map);
        }

        writer.writeByte(player.zoneId())
                .writeShort(player.x())
                .writeShort(player.y())
                .writeByte(map.waypoints().size());
        for (int index = 0; index < map.waypoints().size(); index++) {
            var waypoint = map.waypoints().get(index);
            writer.writeShort(waypoint.x())
                    .writeShort(waypoint.y())
                    .writeByte(waypoint.type())
                    .writeUtf(waypointTargetNames.get(index));
        }

        writer.writeByte(0)
                .writeByte(monsters.size());
        for (MonsterSnapshot monster : monsters) {
            Objects.requireNonNull(monster, "monster");
            writer.writeByte(monster.type())
                    .writeShort(monster.templateId())
                    .writeInt(monster.id())
                    .writeShort(monster.level())
                    .writeByte(monster.levelStatus())
                    .writeShort(monster.x())
                    .writeShort(monster.y())
                    .writeLong(monster.maxHp())
                    .writeLong(monster.hp())
                    .writeByte(monster.status());
        }
        writer.writeShort(0).writeBoolean(false);
        return new Message(MessageName.MAP_INFO, writer.toByteArray());
    }

    private static void writeTemplate(MessageWriter writer, MapTemplate map) throws IOException {
        MapData data = map.data();
        requireNonNegativeShort(data.terrain(), "terrain");
        requireNonNegativeShort(data.row(), "row");
        requireNonNegativeShort(data.column(), "column");
        if (data.background().layers().size() != 3
                || data.background().skyColor().size() != 3
                || data.background().layers().stream().anyMatch(layer -> layer.fillColor().size() != 3)) {
            throw new IOException("map background must contain three RGB layers");
        }

        writer.writeShort(data.terrain())
                .writeUtf(map.name())
                .writeShort(data.row())
                .writeShort(data.column());

        if (data.collision().type() == MapData.CollisionType.GRID) {
            if (data.collision().data() == null) {
                throw new IOException("GRID map missing collision data for map " + map.id());
            }
            writer.writeUtf(data.collision().data());
        } else {
            writer.writeUtf("");
        }

        for (MapData.Layer layer : data.background().layers()) {
            requireBackgroundImage(layer.image());
            writer.writeShort(layer.image());
        }
        for (int value : data.background().skyColor()) {
            requireNonNegativeShort(value, "background sky color");
            writer.writeShort(value);
        }
        for (MapData.Layer layer : data.background().layers()) {
            for (int value : layer.fillColor()) {
                requireNonNegativeShort(value, "background fill color");
                writer.writeShort(value);
            }
        }

        boolean line = data.collision().type() == MapData.CollisionType.LINE;
        writer.writeBoolean(line);
        if (line) {
            writer.writeUtf(lineJson(data));
        }
    }

    private static String lineJson(MapData data) {
        JsonObject root = new JsonObject();
        root.addProperty("MapWidth", data.width());
        root.addProperty("MapHeight", data.height());
        JsonArray lines = new JsonArray();
        for (MapData.Line source : data.collision().lines()) {
            JsonObject line = new JsonObject();
            line.addProperty("Type", source.type().name());
            JsonArray points = new JsonArray();
            for (MapData.Point sourcePoint : source.points()) {
                JsonArray point = new JsonArray();
                point.add(sourcePoint.x());
                point.add(sourcePoint.y());
                points.add(point);
            }
            line.add("Points", points);
            lines.add(line);
        }
        root.add("Lines", lines);
        return GSON.toJson(root);
    }

    private static void requireNonNegativeShort(int value, String label) throws IOException {
        if (value < 0 || value > Short.MAX_VALUE) {
            throw new IOException(label + " must fit 0..32767: " + value);
        }
    }

    private static void requireBackgroundImage(int value) throws IOException {
        if (value < -1 || value > Short.MAX_VALUE) {
            throw new IOException("background image must fit -1..32767: " + value);
        }
    }
}
