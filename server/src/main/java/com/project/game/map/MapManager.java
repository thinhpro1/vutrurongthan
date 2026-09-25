package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Quản lý các runtime Map và điều phối chuyển Player giữa các Map. */
public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    private static final int DEATH_RETURN_MAP_ID = 0;
    private static final int DEATH_RETURN_ZONE_ID = 0;
    private static final int DEATH_RETURN_X = 1250;
    private static final int DEATH_RETURN_Y = 648;

    private final java.util.Map<Integer, Map> maps;
    private final AreaService area;

    public MapManager(java.util.Map<Integer, MapTemplate> catalog,
                      MonsterFactory monsterFactory,
                      PlayerPacketWriter packets) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(monsterFactory, "monsterFactory");
        this.area = new AreaService(Objects.requireNonNull(packets, "packets"));

        TreeMap<Integer, Map> runtimeMaps = new TreeMap<>();
        catalog.forEach((mapId, template) -> {
            if (mapId == null || template == null) {
                throw new NullPointerException("catalog must not contain null entries");
            }
            if (mapId != template.id()) {
                throw new IllegalArgumentException("map catalog key does not match map id");
            }
            if ("ONLINE".equals(template.type())) {
                runtimeMaps.put(mapId, new Map(template, monsterFactory));
            }
        });
        this.maps = java.util.Collections.unmodifiableMap(runtimeMaps);
    }

    /** Tìm runtime Map đang mở mà không tạo Map mới. */
    public Map findMap(int mapId) {
        return maps.get(mapId);
    }

    /** Lấy runtime Map đang mở hoặc báo lỗi nếu Map không hợp lệ. */
    public Map getMap(int mapId) {
        Map map = findMap(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown or offline map " + mapId);
        }
        return map;
    }

    /**
     * Trả về toàn bộ Zone hiện có theo thứ tự Map rồi Zone.
     * Tạm thời giữ cho MonsterManager.update(); sẽ xem xét lại ở P2.
     */
    public List<Zone> zones() {
        return maps.values().stream()
                .sorted(Comparator.comparingInt(Map::id))
                .flatMap(map -> map.zones().stream())
                .toList();
    }

    /** Gia nhập Zone sau khi client hoàn tất tải bản đồ. */
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

                    return new Join(true,
                            area.addPlayer(session, session.player(), admission.existing()));
                }
            });
            closeRejected(result.rejectedObservers());
            return result.accepted();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Tách Session và trả về bản PlayerSaveData được chụp trong Zone owner. */
    public PlayerSaveData leave(Session session) {
        if (session == null || session.player() == null) {
            return null;
        }
        Zone zone = session.zone();
        if (zone == null) {
            Zone pendingZone = resolveZone(session.player().mapId(), session.player().zoneId());
            if (pendingZone != null) {
                try {
                    pendingZone.call(() -> {
                        pendingZone.cancelReservation(session);
                        return null;
                    });
                } catch (RejectedExecutionException exception) {
                    LOGGER.log(Level.FINE,
                            "Không thể hủy reservation vì Zone đã dừng: session=" + session.id(),
                            exception);
                }
            }
            return PlayerSaveData.capture(session.player());
        }

        try {
            LeaveResult result = zone.call(() -> {
                synchronized (zone) {
                    Player player = session.player();
                    if (player == null) {
                        return new LeaveResult(null, List.of());
                    }
                    if (!zone.hasPlayer(session)) {
                        session.clearZone(zone);
                        return new LeaveResult(PlayerSaveData.capture(player), List.of());
                    }
                    if (!zone.removePlayer(session)) {
                        return new LeaveResult(null, List.of());
                    }
                    var rejected = area.removePlayer(session, player.id(), zone.members());
                    session.clearZone(zone);
                    return new LeaveResult(PlayerSaveData.capture(player), rejected);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.saveData();
        } catch (RejectedExecutionException exception) {
            LOGGER.log(Level.WARNING,
                    "Không thể detach Player vì Zone đã dừng: session=" + session.id(), exception);
            return null;
        }
    }

    /** Di chuyển Player trong Zone owner rồi phát thông báo ra khu vực. */
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
                    return new MoveDelivery(true,
                            area.move(session, player, zone.members()));
                }
            });
            closeRejected(result.rejectedObservers());
            return result.moved();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    /** Trả Player đã chết về town và trả handoff bất biến cho handler. */
    public MapChange returnTownFromDeath(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return null;
        }
        Zone sourceZone = session.zone();
        Zone townZone = resolveZone(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID);
        if (sourceZone == null || townZone == null) {
            return null;
        }
        Player sourcePlayer = session.player();
        if (sourcePlayer == null) {
            return null;
        }

        boolean reserved = sourceZone != townZone && reserve(townZone, session);
        if (sourceZone != townZone && !reserved) {
            return null;
        }

        AtomicBoolean committed = new AtomicBoolean();
        try {
            Transition result = sourceZone.call(() -> {
                synchronized (sourceZone) {
                    Player player = session.player();
                    if (session.state() == SessionState.CLOSED
                            || player == null
                            || player != sourcePlayer
                            || !player.isDead()
                            || session.zone() != sourceZone
                            || !sourceZone.hasPlayer(session)) {
                        return new Transition(null, List.of());
                    }
                    if (sourceZone == townZone) {
                        player.revive(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID,
                                DEATH_RETURN_X, DEATH_RETURN_Y);
                        return new Transition(
                                new MapChange(PlayerSaveData.capture(player), townZone.zoneId()),
                                List.of());
                    }

                    if (!sourceZone.removePlayer(session)) {
                        return new Transition(null, List.of());
                    }
                    player.revive(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID,
                            DEATH_RETURN_X, DEATH_RETURN_Y);
                    session.clearZone(sourceZone);
                    committed.set(true);
                    var rejected = area.removePlayer(
                            session, player.id(), sourceZone.members());
                    return new Transition(
                            new MapChange(PlayerSaveData.capture(player), townZone.zoneId()),
                            rejected);
                }
            });
            if (result.change() == null && reserved && !committed.get()) {
                cancel(townZone, session);
            }
            closeRejected(result.rejectedObservers());
            return result.change();
        } catch (RejectedExecutionException exception) {
            if (reserved && !committed.get()) {
                cancel(townZone, session);
            }
            return null;
        } catch (RuntimeException exception) {
            if (reserved && !committed.get()) {
                cancel(townZone, session);
            }
            throw exception;
        }
    }

    /** Chọn waypoint và chuyển Player sau khi Zone nguồn xác nhận trạng thái. */
    public MapChange changeMap(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return null;
        }
        Zone sourceZone = session.zone();
        if (sourceZone == null) {
            return null;
        }
        Map sourceMap = findMap(sourceZone.mapId());
        if (sourceMap == null) {
            return null;
        }

        TransitionIntent intent;
        try {
            intent = sourceZone.call(() -> {
                synchronized (sourceZone) {
                    Player player = session.player();
                    if (session.state() == SessionState.CLOSED
                            || player == null
                            || player.isDead()
                            || session.zone() != sourceZone
                            || !sourceZone.hasPlayer(session)) {
                        return null;
                    }
                    Waypoint waypoint = sourceMap.findWaypoint(player.x(), player.y());
                    if (waypoint == null) {
                        return null;
                    }
                    return new TransitionIntent(
                            player, player.mapId(), player.zoneId(), player.x(), player.y(), waypoint);
                }
            });
        } catch (RejectedExecutionException exception) {
            return null;
        }
        if (intent == null) {
            return null;
        }

        Zone destinationZone = resolveZone(intent.waypoint().goMap(), 0);
        if (destinationZone == null) {
            return null;
        }

        boolean reserved = sourceZone != destinationZone && reserve(destinationZone, session);
        if (sourceZone != destinationZone && !reserved) {
            return null;
        }

        AtomicBoolean committed = new AtomicBoolean();
        try {
            Transition result = sourceZone.call(() -> {
                synchronized (sourceZone) {
                    Player player = session.player();
                    Waypoint currentWaypoint = sourceMap.findWaypoint(intent.x(), intent.y());
                    if (session.state() == SessionState.CLOSED
                            || player == null
                            || player != intent.player()
                            || player.isDead()
                            || session.zone() != sourceZone
                            || !sourceZone.hasPlayer(session)
                            || player.mapId() != intent.mapId()
                            || player.zoneId() != intent.zoneId()
                            || player.x() != intent.x() || player.y() != intent.y()
                            || !intent.waypoint().equals(currentWaypoint)) {
                        return new Transition(null, List.of());
                    }
                    if (sourceZone == destinationZone) {
                        player.changeMap(intent.waypoint().goMap(), 0,
                                intent.waypoint().goX(), intent.waypoint().goY());
                        return new Transition(
                                new MapChange(PlayerSaveData.capture(player), destinationZone.zoneId()),
                                List.of());
                    }

                    if (!sourceZone.removePlayer(session)) {
                        return new Transition(null, List.of());
                    }
                    player.changeMap(intent.waypoint().goMap(), 0,
                            intent.waypoint().goX(), intent.waypoint().goY());
                    session.clearZone(sourceZone);
                    committed.set(true);
                    var rejected = area.removePlayer(
                            session, player.id(), sourceZone.members());
                    return new Transition(
                            new MapChange(PlayerSaveData.capture(player), destinationZone.zoneId()),
                            rejected);
                }
            });
            if (result.change() == null && reserved && !committed.get()) {
                cancel(destinationZone, session);
            }
            closeRejected(result.rejectedObservers());
            return result.change();
        } catch (RejectedExecutionException exception) {
            if (reserved && !committed.get()) {
                cancel(destinationZone, session);
            }
            return null;
        } catch (RuntimeException exception) {
            if (reserved && !committed.get()) {
                cancel(destinationZone, session);
            }
            throw exception;
        }
    }

    private Zone resolveZone(int mapId, int zoneId) {
        Map map = findMap(mapId);
        if (map == null) {
            return null;
        }
        return map.findZone(zoneId);
    }

    private static boolean reserve(Zone zone, Session session) {
        try {
            Zone.ReserveStatus status = zone.call(() -> zone.reservePlayer(session));
            return status == Zone.ReserveStatus.RESERVED
                    || status == Zone.ReserveStatus.ALREADY_RESERVED;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private static void cancel(Zone zone, Session session) {
        try {
            zone.call(() -> {
                zone.cancelReservation(session);
                return null;
            });
        } catch (RejectedExecutionException ignored) {
            // A stopped destination cannot have an active runtime admission.
        }
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    public record MapChange(PlayerSaveData player, int zoneId) {
        public MapChange {
            Objects.requireNonNull(player, "player");
            if (zoneId < 0) {
                throw new IllegalArgumentException("zoneId must be non-negative");
            }
        }
    }

    private record TransitionIntent(
            Player player, int mapId, int zoneId, int x, int y, Waypoint waypoint) {
    }

    private record Join(boolean accepted, List<Session> rejectedObservers) {
        private Join {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record LeaveResult(PlayerSaveData saveData, List<Session> rejectedObservers) {
        private LeaveResult {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record MoveDelivery(boolean moved, List<Session> rejectedObservers) {
        private MoveDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record Transition(MapChange change, List<Session> rejectedObservers) {
        private Transition {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }
}
