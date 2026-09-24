package com.project.game.network.handler;

import com.project.game.map.MapManager;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MapPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Handles map presence, movement, transitions, death return, and MAP_INFO packets. */
final class MapHandler {
    private final Session session;
    private final MapManager mapManager;
    private final MonsterManager monsterManager;
    private final PlayerService playerService;
    private final GameResources resources;
    private final PlayerPacketWriter playerPackets = new PlayerPacketWriter();
    private final MapPacketWriter mapPackets = new MapPacketWriter();

    MapHandler(Session session, MapManager mapManager, MonsterManager monsterManager,
               PlayerService playerService, GameResources resources) {
        this.session = session;
        this.mapManager = mapManager;
        this.monsterManager = monsterManager;
        this.playerService = playerService;
        this.resources = resources;
    }

    void handleFinishLoadMap(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing FINISH_LOAD_MAP payload bytes");
        }
        if (!mapManager.finishLoad(session)) {
            throw new IOException("cannot join map zone");
        }
    }

    void handleReturnTownFromDie(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing RETURN_TOWN_FROM_DIE payload bytes");
        }
        Optional<PlayerProfile> revived = mapManager.returnTownFromDeath(session);
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
                        "map unavailable: " + player.mapId()));
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

        Optional<PlayerProfile> changed = mapManager.changeMap(
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

        mapManager.movePlayer(session, x, y);
    }

    void sendMapInfo(PlayerProfile player) throws IOException {
        var map = resources.map(player.mapId())
                .orElseThrow(() -> new IOException(
                        "map unavailable: " + player.mapId()));
        boolean sendTemplate = !session.hasSentMapTemplate(map.id());
        var waypointTargetNames = new ArrayList<String>(map.waypoints().size());
        for (var waypoint : map.waypoints()) {
            var target = resources.map(waypoint.goMap())
                    .orElseThrow(() -> new IOException(
                            "waypoint target map unavailable: " + waypoint.goMap()));
            waypointTargetNames.add(target.name());
        }
        List<MonsterSnapshot> monsters;
        if (!mapManager.ensureZone(map.id(), player.zoneId())) {
            throw new IOException("invalid map zone: " + map.id() + "/" + player.zoneId());
        }
        try {
            monsters = monsterManager.monsterSnapshots(map.id(), player.zoneId());
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid map zone: " + map.id() + "/" + player.zoneId(), exception);
        }
        Message packet = mapPackets.mapInfo(
                player, map, sendTemplate, waypointTargetNames, monsters);
        if (session.send(packet) && sendTemplate) {
            session.markMapTemplateSent(map.id());
        }
    }
}
