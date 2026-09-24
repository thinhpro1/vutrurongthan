package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/** Sở hữu danh mục bản đồ, Zone và điều phối các chuyển động cấp bản đồ. */
public final class MapManager {
    private static final int DEATH_RETURN_MAP_ID = 0;
    private static final int DEATH_RETURN_ZONE_ID = 0;
    private static final int DEATH_RETURN_X = 1250;
    private static final int DEATH_RETURN_Y = 648;

    private record ZoneKey(int mapId, int zoneId) {
    }

    private final Map<Integer, MapTemplate> maps;
    private final MonsterFactory monsterFactory;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();
    private final PlayerPacketWriter packets;

    public MapManager(Map<Integer, MapTemplate> maps, MonsterFactory monsterFactory,
                      PlayerPacketWriter packets) {
        Objects.requireNonNull(maps, "maps");
        TreeMap<Integer, MapTemplate> copiedMaps = new TreeMap<>();
        maps.forEach((mapId, map) -> {
            if (mapId == null || map == null) {
                throw new NullPointerException("maps must not contain null entries");
            }
            if (mapId != map.id()) {
                throw new IllegalArgumentException("map catalog key does not match map id");
            }
            copiedMaps.put(mapId, map);
        });
        this.maps = Collections.unmodifiableMap(copiedMaps);
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
        this.packets = Objects.requireNonNull(packets, "packets");
        for (MapTemplate map : this.maps.values()) {
            if ("ONLINE".equals(map.type())) {
                for (int zoneId = 0; zoneId < map.minZone(); zoneId++) {
                    zones.put(new ZoneKey(map.id(), zoneId), create(map, zoneId));
                }
            }
        }
    }

    /** Trả về Zone hiện có mà không tạo mới. */
    public Zone findZone(int mapId, int zoneId) {
        return zones.get(new ZoneKey(mapId, zoneId));
    }

    /** Trả về Zone hợp lệ theo chính sách, tạo nguyên tử khi được yêu cầu lần đầu. */
    public Zone getZone(int mapId, int zoneId) {
        MapTemplate map = requireOnlineMap(mapId, zoneId);
        ZoneKey key = new ZoneKey(mapId, zoneId);
        return zones.computeIfAbsent(key, ignored -> create(map, zoneId));
    }

    /** Trả về toàn bộ Zone theo thứ tự bản đồ/khu vực ổn định. */
    public List<Zone> zones() {
        return zones.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparingInt(ZoneKey::mapId)
                                .thenComparingInt(ZoneKey::zoneId)))
                .map(Map.Entry::getValue)
                .toList();
    }

    public boolean finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        Player player = session.player();
        Zone currentZone = session.zone();
        if (player == null) {
            return false;
        }
        if (currentZone != null) {
            return currentZone.mapId() == player.mapId()
                    && currentZone.zoneId() == player.zoneId()
                    && currentZone.hasPlayer(session);
        }
        Zone zone = resolveZone(player.mapId(), player.zoneId());
        if (zone == null) {
            return false;
        }

        try {
            Join result = zone.tryCall(() -> {
                synchronized (zone) {
                    if (session.state() == SessionState.CLOSED || session.player() == null) {
                        return new Join(false, List.of());
                    }
                    if (session.zone() != null) {
                        return new Join(session.zone() == zone && zone.hasPlayer(session), List.of());
                    }

                    Zone.JoinResult admission = zone.addPlayer(session);
                    if (admission.status() == Zone.JoinStatus.FULL
                            || admission.status() == Zone.JoinStatus.PLAYER_ID_CONFLICT) {
                        return new Join(false, List.of());
                    }
                    session.bindZone(zone);
                    if (admission.status() == Zone.JoinStatus.ALREADY_PRESENT) {
                        return new Join(true, List.of());
                    }

                    Player current = session.player();
                    List<Session> rejectedObservers = new ArrayList<>();
                    for (Session member : admission.existing()) {
                        if (member == session || member.state() == SessionState.CLOSED
                                || member.player() == null) {
                            continue;
                        }
                        if (!session.trySend(packets.addPlayer(member.player()))) {
                            rejectedObservers.add(session);
                            return new Join(true, rejectedObservers);
                        }
                        if (!member.trySend(packets.addPlayer(current))) {
                            rejectedObservers.add(member);
                        }
                    }
                    return new Join(true, rejectedObservers);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.accepted();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Rời membership theo liên kết định tuyến Zone hiện tại của Session. */
    public void leave(Session session) {
        if (session == null || session.player() == null) {
            return;
        }
        Zone zone = session.zone();
        if (zone == null) {
            return;
        }
        Player leaving = session.player();

        try {
            List<Session> rejectedObservers = zone.call(() -> {
                synchronized (zone) {
                    if (!zone.removePlayer(session)) {
                        session.clearZone(zone);
                        return List.of();
                    }

                    session.clearZone(zone);
                    Message packet = packets.removePlayer(leaving.id());
                    List<Session> rejected = new ArrayList<>();
                    for (Session member : zone.members()) {
                        if (member != session && member.state() != SessionState.CLOSED
                                && !member.trySend(packet)) {
                            rejected.add(member);
                        }
                    }
                    return List.copyOf(rejected);
                }
            });
            closeRejected(rejectedObservers);
        } catch (RejectedExecutionException exception) {
            // Zone đã dừng, không thể nhận thêm tác vụ có thẩm quyền.
        }
    }

    public boolean movePlayer(Session session, int x, int y) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        Zone zone = session.zone();
        if (zone == null) {
            return false;
        }

        try {
            MoveDelivery result = zone.tryCall(() -> {
                synchronized (zone) {
                    Player player = session.player();
                    if (player == null || player.isDead() || !zone.hasPlayer(session)
                            || !player.move(x, y)) {
                        return new MoveDelivery(false, List.of());
                    }

                    Message packet = packets.movePlayer(player.id(), player.x(), player.y());
                    List<Session> rejectedObservers = new ArrayList<>();
                    for (Session member : zone.members()) {
                        if (member != session && member.state() != SessionState.CLOSED
                                && !member.trySend(packet)) {
                            rejectedObservers.add(member);
                        }
                    }
                    return new MoveDelivery(true, rejectedObservers);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.moved();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public boolean returnTownFromDeath(Session session) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.player() == null || !session.player().isDead()) {
            return false;
        }

        Zone townZone = resolveZone(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID);
        Zone sourceZone = session.zone();
        if (townZone == null || sourceZone == null || !canAddPlayer(townZone, session)) {
            return false;
        }

        try {
            Transition result = sourceZone.call(() -> {
                synchronized (sourceZone) {
                    Player player = session.player();
                    if (player == null || !player.isDead() || !sourceZone.hasPlayer(session)) {
                        return new Transition(false, List.of());
                    }
                    if (sourceZone == townZone) {
                        player.revive(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID,
                                DEATH_RETURN_X, DEATH_RETURN_Y);
                        return new Transition(true, List.of());
                    }

                    boolean removed = sourceZone.removePlayer(session);
                    if (!removed) {
                        return new Transition(false, List.of());
                    }
                    Message packet = packets.removePlayer(player.id());
                    session.clearZone(sourceZone);
                    player.revive(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID,
                            DEATH_RETURN_X, DEATH_RETURN_Y);
                    List<Session> rejectedObservers = new ArrayList<>();
                    for (Session member : sourceZone.members()) {
                        if (member != session && member.state() != SessionState.CLOSED
                                && !member.trySend(packet)) {
                            rejectedObservers.add(member);
                        }
                    }
                    return new Transition(true, rejectedObservers);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.success();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Chọn waypoint và chuyển Player sau khi Zone nguồn nhận quyền thay đổi trạng thái. */
    public boolean changeMap(Session session) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.player() == null || session.zone() == null) {
            return false;
        }
        Player observed = session.player();
        int observedMapId = observed.mapId();
        int observedZoneId = observed.zoneId();
        int observedX = observed.x();
        int observedY = observed.y();
        MapTemplate sourceMap = maps.get(observedMapId);
        if (sourceMap == null || observed.isDead()) {
            return false;
        }
        Waypoint waypoint = sourceMap.waypoints().stream()
                .filter(candidate -> candidate.contains(observedX, observedY))
                .findFirst()
                .orElse(null);
        if (waypoint == null) {
            return false;
        }

        Zone destinationZone = resolveZone(waypoint.goMap(), 0);
        Zone sourceZone = session.zone();
        if (destinationZone == null || !canAddPlayer(destinationZone, session)) {
            return false;
        }

        try {
            Transition result = sourceZone.call(() -> {
                synchronized (sourceZone) {
                    Player player = session.player();
                    if (player == null || player.isDead() || !sourceZone.hasPlayer(session)
                            || player.mapId() != observedMapId
                            || player.zoneId() != observedZoneId
                            || player.x() != observedX || player.y() != observedY) {
                        return new Transition(false, List.of());
                    }
                    if (sourceZone == destinationZone) {
                        player.changeMap(waypoint.goMap(), 0, waypoint.goX(), waypoint.goY());
                        return new Transition(true, List.of());
                    }

                    boolean removed = sourceZone.removePlayer(session);
                    if (!removed) {
                        return new Transition(false, List.of());
                    }
                    Message packet = packets.removePlayer(player.id());
                    session.clearZone(sourceZone);
                    player.changeMap(waypoint.goMap(), 0, waypoint.goX(), waypoint.goY());
                    List<Session> rejectedObservers = new ArrayList<>();
                    for (Session member : sourceZone.members()) {
                        if (member != session && member.state() != SessionState.CLOSED
                                && !member.trySend(packet)) {
                            rejectedObservers.add(member);
                        }
                    }
                    return new Transition(true, rejectedObservers);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.success();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = findZone(mapId, zoneId);
        return zone == null ? 0 : zone.size();
    }

    /** Phân giải Zone hợp lệ trước khi handler phát trạng thái bản đồ. */
    public boolean ensureZone(int mapId, int zoneId) {
        return resolveZone(mapId, zoneId) != null;
    }

    private Zone resolveZone(int mapId, int zoneId) {
        try {
            return getZone(mapId, zoneId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean canAddPlayer(Zone zone, Session session) {
        return zone.canAddPlayer(session);
    }

    private MapTemplate requireOnlineMap(int mapId, int zoneId) {
        MapTemplate map = maps.get(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown map " + mapId);
        }
        if (!"ONLINE".equals(map.type())) {
            throw new IllegalArgumentException("map " + mapId + " is not ONLINE");
        }
        if (zoneId < 0 || zoneId >= map.maxZone()) {
            throw new IllegalArgumentException(
                    "zone " + zoneId + " is outside map " + mapId + " bound 0.."
                            + (map.maxZone() - 1));
        }
        return map;
    }

    private Zone create(MapTemplate map, int zoneId) {
        return new Zone(map.id(), zoneId, map.maxPlayer(), monsterFactory.createForMap(map.id()));
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    private record Join(boolean accepted, List<Session> rejectedObservers) {
        private Join {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record MoveDelivery(boolean moved, List<Session> rejectedObservers) {
        private MoveDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record Transition(boolean success, List<Session> rejectedObservers) {
        private Transition {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }
}
