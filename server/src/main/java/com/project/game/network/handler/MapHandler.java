package com.project.game.network.handler;

import com.project.game.map.MapManager;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MapPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.resource.GameResources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Xử lý hiện diện, di chuyển, chuyển Map, hồi sinh và packet MAP_INFO. */
final class MapHandler {
    private static final Logger LOGGER = Logger.getLogger(MapHandler.class.getName());
    private final Session session;
    private final MapManager mapManager;
    private final MonsterManager monsterManager;
    private final PlayerRepository playerRepository;
    private final GameResources resources;
    private final PlayerPacketWriter playerPackets = new PlayerPacketWriter();
    private final MapPacketWriter mapPackets = new MapPacketWriter();

    MapHandler(Session session, MapManager mapManager, MonsterManager monsterManager,
               PlayerRepository playerRepository, GameResources resources) {
        this.session = session;
        this.mapManager = mapManager;
        this.monsterManager = monsterManager;
        this.playerRepository = playerRepository;
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
        MapManager.MapChange change = mapManager.returnTownFromDeath(session);
        if (change == null) {
            return;
        }

        save(change.player());
        sendMapInfo(change.player(), change.zoneId());
        if (session.state() != SessionState.CLOSED) {
            PlayerSaveData player = change.player();
            session.send(playerPackets.wakeUpFromDie(
                    player.id(), player.x(), player.y(), player.hp(), player.mp()));
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
        MapManager.MapChange change = mapManager.changeMap(session);
        if (change == null) {
            return;
        }
        save(change.player());
        sendMapInfo(change.player(), change.zoneId());
    }

    void handlePlayerMove(Message message) throws IOException {
        Player player = session.player();
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

    void sendMapInfo(Player player) throws IOException {
        sendMapInfo(PlayerSaveData.capture(player), player.zoneId());
    }

    private void save(PlayerSaveData player) {
        try {
            playerRepository.save(player);
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER transition save failed playerId=" + player.id(), exception);
        }
    }

    private void sendMapInfo(PlayerSaveData player, int zoneId) throws IOException {
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
        if (!mapManager.ensureZone(map.id(), zoneId)) {
            throw new IOException("invalid map zone: " + map.id() + "/" + zoneId);
        }
        try {
            monsters = monsterManager.monsterSnapshots(map.id(), zoneId);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid map zone: " + map.id() + "/" + zoneId, exception);
        }
        Message packet = mapPackets.mapInfo(
                zoneId, player.x(), player.y(), map, sendTemplate, waypointTargetNames, monsters);
        if (session.send(packet) && sendTemplate) {
            session.markMapTemplateSent(map.id());
        }
    }
}
