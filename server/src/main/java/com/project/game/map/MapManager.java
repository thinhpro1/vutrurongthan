package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Quản lý registry Map public và điều phối chuyển Player giữa các Map public. */
public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    static final int HOME_MAP_ID = 0;
    static final int HOME_ZONE_ID = 0;
    static final int HOME_X = 1250;
    static final int HOME_Y = 648;

    private final java.util.Map<Integer, Map> maps;

    public MapManager(java.util.Map<Integer, MapTemplate> catalog,
                      MonsterManager monsterManager,
                      AreaService area) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(monsterManager, "monsterManager");
        Objects.requireNonNull(area, "area");

        TreeMap<Integer, Map> runtimeMaps = new TreeMap<>();
        for (java.util.Map.Entry<Integer, MapTemplate> entry : catalog.entrySet()) {
            Integer mapId = entry.getKey();
            MapTemplate template = entry.getValue();
            if (mapId == null || template == null) {
                throw new NullPointerException("catalog must not contain null entries");
            }
            if (mapId != template.id()) {
                throw new IllegalArgumentException("map catalog key does not match map id");
            }
            if ("ONLINE".equals(template.type())) {
                runtimeMaps.put(mapId, new Map(template, monsterManager, area));
            }
        }
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

    /** Trả về các Map public đã đăng ký theo thứ tự id ổn định. */
    public List<Map> maps() {
        return List.copyOf(maps.values());
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
            // A joined Session must be present in its Zone; Player location only
            // participates in checking that joined invariant.
            return currentZone.mapId() == player.mapId()
                    && currentZone.zoneId() == player.zoneId()
                    && currentZone.hasPlayer(session);
        }
        // A detached Session uses Player location to route the pending handoff.
        Zone zone = findZone(player.mapId(), player.zoneId());
        if (zone == null) {
            return false;
        }

        return zone.enter(session);
    }

    /** Tách Session và trả về bản PlayerSaveData được chụp trong Zone owner. */
    public PlayerSaveData leave(Session session) {
        if (session == null || session.player() == null) {
            return null;
        }
        Zone zone = session.zone();
        if (zone == null) {
            Zone pendingZone = findZone(session.player().mapId(), session.player().zoneId());
            if (pendingZone != null) {
                try {
                    pendingZone.cancel(session);
                } catch (RejectedExecutionException exception) {
                    LOGGER.log(Level.FINE,
                            "Không thể hủy reservation vì Zone đã dừng: session=" + session.id(),
                            exception);
                }
            }
            return PlayerSaveData.capture(session.player());
        }

        try {
            return zone.leave(session);
        } catch (RejectedExecutionException exception) {
            LOGGER.log(Level.WARNING,
                    "Không thể detach Player vì Zone đã dừng: session=" + session.id(), exception);
            return null;
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
        Zone.requireOutsideRuntimeWorker("changeMap");
        Map sourceMap = findMap(sourceZone.mapId());
        if (sourceMap == null) {
            return null;
        }

        ChangeState state;
        try {
            state = sourceZone.call(() -> {
                Player player = session.player();
                if (!canLeave(session, sourceZone, player) || player.isDead()) {
                    return null;
                }
                Waypoint waypoint = sourceMap.findWaypoint(player.x(), player.y());
                return waypoint == null ? null : new ChangeState(player,
                        player.mapId(), player.zoneId(), player.x(), player.y(), waypoint);
            });
        } catch (RejectedExecutionException exception) {
            return null;
        }
        if (state == null) {
            return null;
        }

        Zone destination = findZone(state.waypoint().goMap(), 0);
        return destination == null ? null
                : changeZone(session, sourceZone, destination, sourceMap, state);
    }

    /** Protocol death-return: chỉ Player chết được hồi sinh và chuyển về home. */
    public MapChange returnHomeFromDeath(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return null;
        }
        Zone sourceZone = session.zone();
        if (sourceZone == null) {
            return null;
        }
        Zone.requireOutsideRuntimeWorker("returnHomeFromDeath");
        Zone home = findZone(HOME_MAP_ID, HOME_ZONE_ID);
        if (home == null) {
            return null;
        }

        ChangeState state;
        try {
            state = sourceZone.call(() -> {
                Player player = session.player();
                if (!canLeave(session, sourceZone, player) || !player.isDead()) {
                    return null;
                }
                return new ChangeState(player, player.mapId(), player.zoneId(),
                        player.x(), player.y(), null);
            });
        } catch (RejectedExecutionException exception) {
            return null;
        }
        return state == null ? null : changeZone(session, sourceZone, home, null, state);
    }

    /** Reserve ở destination rồi revalidate/commit trên source; không chờ hai writer lồng nhau. */
    private MapChange changeZone(Session session, Zone source, Zone destination,
                                 Map sourceMap, ChangeState state) {
        boolean reserved = source != destination && reserveDestination(destination, session);
        if (source != destination && !reserved) {
            return null;
        }

        AtomicBoolean committed = new AtomicBoolean();
        List<Session> rejected = new ArrayList<>();
        try {
            MapChange change = source.call(() -> {
                Player player = session.player();
                Waypoint waypoint = state.waypoint();
                boolean home = waypoint == null;
                if (!canLeave(session, source, player)
                        || player != state.player()
                        || player.isDead() != home
                        || player.mapId() != state.mapId()
                        || player.zoneId() != state.zoneId()
                        || player.x() != state.x() || player.y() != state.y()
                        || (!home && !waypoint.equals(sourceMap.findWaypoint(state.x(), state.y())))) {
                    return null;
                }
                if (source != destination && !source.removePlayer(session)) {
                    return null;
                }
                if (home) {
                    player.revive(HOME_MAP_ID, HOME_ZONE_ID, HOME_X, HOME_Y);
                } else {
                    player.changeMap(waypoint.goMap(), 0, waypoint.goX(), waypoint.goY());
                }
                // Handoff đã commit trước delivery: lỗi packet không được hủy slot đích.
                committed.set(true);
                if (source != destination) {
                    source.detach(session, rejected);
                }
                return new MapChange(PlayerSaveData.capture(player), destination.zoneId());
            });
            closeRejected(rejected);
            return change;
        } catch (RejectedExecutionException exception) {
            return null;
        } finally {
            if (reserved && !committed.get()) {
                cancelReservation(destination, session);
            }
        }
    }

    private static boolean canLeave(Session session, Zone source, Player player) {
        return session.state() != SessionState.CLOSED
                && player != null
                && session.zone() == source
                && source.hasPlayer(session);
    }

    /** Source state bất biến; waypoint null biểu thị yêu cầu death-return về home. */
    private record ChangeState(Player player, int mapId, int zoneId, int x, int y,
                               Waypoint waypoint) {
    }

    private boolean reserveDestination(Zone zone, Session session) {
        try {
            Zone.ReserveStatus status = zone.reserve(session);
            return status == Zone.ReserveStatus.RESERVED
                    || status == Zone.ReserveStatus.ALREADY_RESERVED;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void cancelReservation(Zone zone, Session session) {
        try {
            zone.cancel(session);
        } catch (RejectedExecutionException ignored) {
            // A stopped destination cannot have an active runtime admission.
        }
    }

    private Zone findZone(int mapId, int zoneId) {
        Map map = findMap(mapId);
        if (map == null) {
            return null;
        }
        return map.findZone(zoneId);
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    public record MapChange(PlayerSaveData saveData, int zoneId) {
        public MapChange {
            Objects.requireNonNull(saveData, "saveData");
            if (zoneId < 0) {
                throw new IllegalArgumentException("zoneId must be non-negative");
            }
        }
    }

}
