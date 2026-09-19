package com.project.game.network.packet;

import com.project.game.map.LegacyMapTemplate;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Serializes the legacy MAP_INFO payload without owning map/session state. */
public final class MapPacketWriter {
    public Message mapInfo(
            PlayerProfile player,
            LegacyMapTemplate map,
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
        LegacyPlayerCompatibilityValidator.validateMapInfo(player, map.id());

        MessageWriter writer = new MessageWriter().writeShort(map.id());
        if (includeTemplate) {
            writer.writeShort(map.iconId())
                    .writeUtf(map.name())
                    .writeShort(map.row())
                    .writeShort(map.column())
                    .writeUtf(map.data());
            for (int imageId : map.imagesBgr()) {
                writer.writeShort(imageId);
            }
            for (var colorRow : map.colorsBgr()) {
                for (int value : colorRow) {
                    writer.writeShort(value);
                }
            }
            writer.writeBoolean(map.line());
            if (map.line()) {
                if (map.dataLine() == null) {
                    throw new IOException("line map missing dataLine for map " + map.id());
                }
                writer.writeUtf(map.dataLine());
            }
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
}
