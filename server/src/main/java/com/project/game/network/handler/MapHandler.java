package com.project.game.network.handler;

import com.project.game.map.MapService;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.LegacyPlayerCompatibilityValidator;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;

import java.io.IOException;
import java.util.Optional;

/** Handles map presence, movement, transitions, death return, and MAP_INFO packets. */
final class MapHandler {
    private final Session session;
    private final MapService mapService;
    private final PlayerService playerService;
    private final GameResources resources;
    private final PlayerPacketWriter playerPackets = new PlayerPacketWriter();

    MapHandler(Session session, MapService mapService, PlayerService playerService,
               GameResources resources) {
        this.session = session;
        this.mapService = mapService;
        this.playerService = playerService;
        this.resources = resources;
    }

    void handleFinishLoadMap(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing FINISH_LOAD_MAP payload bytes");
        }
        mapService.finishLoad(session);
    }

    void handleReturnTownFromDie(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing RETURN_TOWN_FROM_DIE payload bytes");
        }
        Optional<PlayerProfile> revived = mapService.returnTownFromDeath(session);
        if (revived.isEmpty()) {
            return;
        }

        PlayerProfile player = revived.orElseThrow();
        playerService.checkpoint(player);
        sendMapInfo(player);
        if (session.state() != SessionState.CLOSED) {
            session.send(playerPackets.wakeUpFromDie(player));
        }
    }

    void handleUnsupportedWakeUpFromDie(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing WAKE_UP_FROM_DIE request payload bytes");
        }
    }

    void handleRequestChangeMap(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing REQUEST_CHANGE_MAP payload bytes");
        }
        PlayerProfile player = session.player();
        if (player == null) {
            throw new IOException("REQUEST_CHANGE_MAP without bound player");
        }

        var map = resources.map(player.mapId())
                .orElseThrow(() -> new IOException(
                        "legacy map bootstrap unavailable for map " + player.mapId()));
        var waypoint = map.waypoints().stream()
                .filter(candidate -> candidate.contains(player.x(), player.y()))
                .findFirst()
                .orElse(null);
        if (waypoint == null) {
            return;
        }

        var destination = resources.map(waypoint.goMap()).orElse(null);
        if (destination == null) {
            return;
        }

        Optional<PlayerProfile> changed = mapService.changeMap(
                session,
                player.mapId(),
                player.zoneId(),
                waypoint.goMap(),
                0,
                waypoint.goX(),
                waypoint.goY());
        if (changed.isEmpty()) {
            return;
        }
        PlayerProfile changedPlayer = changed.orElseThrow();
        playerService.checkpoint(changedPlayer);
        sendMapInfo(changedPlayer);
    }

    void handlePlayerMove(Message message) throws IOException {
        PlayerProfile player = session.player();
        if (player == null) {
            throw new IOException("PLAYER_MOVE without bound player");
        }

        var reader = message.reader();
        int x = reader.readShort();
        int y = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IOException("trailing PLAYER_MOVE payload bytes");
        }

        mapService.movePlayer(session, x, y);
    }

    void sendMapInfo(PlayerProfile player) throws IOException {
        var map = resources.map(player.mapId())
                .orElseThrow(() -> new IOException(
                        "legacy map bootstrap unavailable for map " + player.mapId()));
        LegacyPlayerCompatibilityValidator.validateMapInfo(player, map.id());

        boolean sendTemplate = !session.hasSentMapTemplate(map.id());
        MessageWriter writer = new MessageWriter().writeShort(map.id());
        if (sendTemplate) {
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
                .writeShort(player.y());

        var waypoints = map.waypoints();
        if (waypoints.size() > Byte.MAX_VALUE) {
            throw new IOException("too many waypoints for map " + map.id());
        }
        writer.writeByte(waypoints.size());
        for (var waypoint : waypoints) {
            var target = resources.map(waypoint.goMap())
                    .orElseThrow(() -> new IOException(
                            "waypoint target map unavailable: " + waypoint.goMap()));
            writer.writeShort(waypoint.x())
                    .writeShort(waypoint.y())
                    .writeByte(waypoint.type())
                    .writeUtf(target.name());
        }

        writer.writeByte(0);
        var monsters = mapService.monsterSnapshots(map.id(), player.zoneId());
        if (monsters.size() > Byte.MAX_VALUE) {
            throw new IOException("too many monsters for map " + map.id());
        }
        writer.writeByte(monsters.size());
        for (var monster : monsters) {
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

        if (session.send(new Message(MessageName.MAP_INFO, writer.toByteArray())) && sendTemplate) {
            session.markMapTemplateSent(map.id());
        }
    }
}
