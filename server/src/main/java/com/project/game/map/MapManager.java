package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Giữ các Map public và điều phối Player đi giữa các Zone.
 *
 * <p>Chuyển map luôn theo một luồng, không bao giờ chờ hai Zone lồng nhau:
 * <pre>
 * 1. plan    : trên Zone nguồn, Player còn đứng đúng chỗ không → tạo Trip
 * 2. reserve : Zone đích giữ chỗ
 * 3. commit  : trên Zone nguồn, kiểm tra lại Trip → Player đổi vị trí → rời Zone nguồn
 * 4. enter   : client gửi FINISH_LOAD_MAP → {@link #finishLoad} → Zone đích nhận Player
 * </pre>
 */
public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    static final int HOME_MAP_ID = 0;
    static final int HOME_ZONE_ID = 0;
    static final int HOME_X = 1250;
    static final int HOME_Y = 648;

    private final java.util.Map<Integer, Map> maps;
    // Chuyến đi đang chờ FINISH_LOAD_MAP. Chỗ thật sự được giữ ở Zone đích.
    private final java.util.Map<Session, Trip> trips = new ConcurrentHashMap<>();

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
        this.maps = Collections.unmodifiableMap(runtimeMaps);
    }

    /** Tìm Map đang mở mà không tạo Map mới. */
    public Map findMap(int mapId) {
        return maps.get(mapId);
    }

    /** Lấy Map đang mở hoặc báo lỗi nếu Map không hợp lệ. */
    public Map getMap(int mapId) {
        Map map = findMap(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown or offline map " + mapId);
        }
        return map;
    }

    /** Các Map public theo thứ tự id. */
    public List<Map> maps() {
        return List.copyOf(maps.values());
    }

    // ------------------------------------------------------------------
    // Vào Zone sau khi client tải xong map
    // ------------------------------------------------------------------

    /** FINISH_LOAD_MAP: Player vào Zone theo vị trí hiện tại (lúc login hoặc cuối chuyến đi). */
    public boolean finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        Player player = session.player();
        if (player == null) {
            return false;
        }
        Zone current = session.zone();
        if (current != null) {
            // Đã ở trong Zone: chỉ xác nhận vị trí khớp, không vào lại.
            return current.mapId() == player.mapId()
                    && current.zoneId() == player.zoneId()
                    && current.hasPlayer(session);
        }
        Zone zone = findZone(player.mapId(), player.zoneId());
        if (zone == null) {
            return false;
        }
        return zone.enter(session, () -> finishTrip(session, zone));
    }

    /** Chạy trên writer của Zone đích khi Player được nhận: chuyến đi kết thúc. */
    private void finishTrip(Session session, Zone zone) {
        Trip trip = trips.get(session);
        if (trip != null && trip.destination == zone) {
            trips.remove(session, trip);
        }
    }

    // ------------------------------------------------------------------
    // Chuyển map
    // ------------------------------------------------------------------

    /** REQUEST_CHANGE_MAP: Player đứng ở waypoint thì đi sang map của waypoint. */
    public MapChange changeMap(Session session) {
        Zone source = joinedZone(session);
        if (source == null) {
            return null;
        }
        Zone.requireOutsideRuntimeWorker("changeMap");
        Map sourceMap = findMap(source.mapId());
        if (sourceMap == null) {
            return null;
        }
        Trip trip = plan(source, () -> {
            Player player = session.player();
            if (!canLeave(session, source, player) || player.isDead()) {
                return null;
            }
            Waypoint waypoint = sourceMap.findWaypoint(player.x(), player.y());
            if (waypoint == null) {
                return null;
            }
            Zone destination = findZone(waypoint.goMap(), 0);
            return destination == null ? null : new Trip(player, waypoint, destination);
        });
        return trip == null ? null : changeZone(session, source, sourceMap, trip);
    }

    /** RETURN_TOWN_FROM_DIE: chỉ Player đã chết được hồi sinh và đưa về nhà. */
    public MapChange returnHomeFromDeath(Session session) {
        Zone source = joinedZone(session);
        if (source == null) {
            return null;
        }
        Zone.requireOutsideRuntimeWorker("returnHomeFromDeath");
        Zone home = findZone(HOME_MAP_ID, HOME_ZONE_ID);
        if (home == null) {
            return null;
        }
        Trip trip = plan(source, () -> {
            Player player = session.player();
            if (!canLeave(session, source, player) || !player.isDead()) {
                return null;
            }
            return new Trip(player, null, home);
        });
        return trip == null ? null : changeZone(session, source, null, trip);
    }

    /** Bước 2–3: giữ chỗ ở đích, rồi kiểm tra lại và commit trên Zone nguồn. */
    private MapChange changeZone(Session session, Zone source, Map sourceMap, Trip trip) {
        if (trips.putIfAbsent(session, trip) != null) {
            return null; // Đang có một chuyến đi khác.
        }
        Zone destination = trip.destination;
        boolean sameZone = source == destination;
        boolean reserved = false;
        AtomicBoolean committed = new AtomicBoolean();
        try {
            if (!sameZone) {
                reserved = reserve(destination, session);
                if (!reserved) {
                    return null;
                }
            }
            return source.call(() -> {
                Player player = session.player();
                if (!canLeave(session, source, player) || !trip.isStillValid(player, sourceMap)) {
                    return null;
                }
                if (!sameZone && !source.removePlayer(session)) {
                    return null;
                }
                trip.apply(player);
                // Commit trước khi gửi packet: lỗi gửi không được hủy chỗ ở Zone đích.
                committed.set(true);
                if (!sameZone) {
                    source.detach(session);
                }
                return new MapChange(PlayerSaveData.capture(player), destination.zoneId());
            });
        } catch (RejectedExecutionException exception) {
            return null;
        } finally {
            if (!committed.get() && reserved) {
                cancel(destination, session);
            }
            if (!committed.get() || sameZone) {
                trips.remove(session, trip);
            }
        }
    }

    /** Một lần đi: vị trí Player lúc yêu cầu (để kiểm tra lại khi commit) và Zone đích. */
    private static final class Trip {
        private final Player player;
        private final int mapId;
        private final int zoneId;
        private final int x;
        private final int y;
        private final Waypoint waypoint; // null = về nhà khi chết
        private final Zone destination;

        private Trip(Player player, Waypoint waypoint, Zone destination) {
            this.player = player;
            this.mapId = player.mapId();
            this.zoneId = player.zoneId();
            this.x = player.x();
            this.y = player.y();
            this.waypoint = waypoint;
            this.destination = destination;
        }

        private boolean isHome() {
            return waypoint == null;
        }

        /** Player vẫn là người đó, vẫn đứng đúng chỗ và vẫn đúng trạng thái sống/chết. */
        private boolean isStillValid(Player current, Map sourceMap) {
            if (current != player || current.isDead() != isHome()) {
                return false;
            }
            if (current.mapId() != mapId || current.zoneId() != zoneId
                    || current.x() != x || current.y() != y) {
                return false;
            }
            return isHome() || waypoint.equals(sourceMap.findWaypoint(x, y));
        }

        /** Player tự đổi vị trí: hồi sinh ở nhà hoặc ra cửa waypoint. */
        private void apply(Player current) {
            if (isHome()) {
                current.revive(destination.mapId(), destination.zoneId(), HOME_X, HOME_Y);
            } else {
                current.changeMap(destination.mapId(), destination.zoneId(),
                        waypoint.goX(), waypoint.goY());
            }
        }
    }

    // ------------------------------------------------------------------
    // Rời game (disconnect)
    // ------------------------------------------------------------------

    /** Dọn Session khỏi mọi Zone có thể đang giữ nó, rồi mới chụp bản lưu cuối. */
    public PlayerSaveData leave(Session session) {
        Zone.requireOutsideRuntimeWorker("leave");
        if (session == null || session.player() == null) {
            return null;
        }
        List<Zone> owners = new ArrayList<>();
        addOwners(owners, session);
        boolean detached = true;
        try {
            for (int index = 0; index < owners.size(); index++) {
                if (!drop(owners.get(index), session)) {
                    detached = false;
                }
                // Một chuyến đi / enter có thể vừa xong trong lúc Zone trước đang được dọn.
                addOwners(owners, session);
            }
            if (!detached || session.zone() != null) {
                return null;
            }
            trips.remove(session);
            if (owners.isEmpty()) {
                return captureAfterLeave(session);
            }
            return owners.getLast().call(() -> captureAfterLeave(session));
        } catch (RejectedExecutionException exception) {
            LOGGER.log(Level.WARNING,
                    "Không thể chụp Player vì Zone đã dừng: session=" + session.id(), exception);
            return null;
        }
    }

    /** Zone hiện tại, Zone đích của chuyến đi đang chờ và Zone theo vị trí Player. */
    private void addOwners(List<Zone> owners, Session session) {
        addOwner(owners, session.zone());
        addOwner(owners, tripDestination(session));
        addOwner(owners, findZone(session.player().mapId(), session.player().zoneId()));
    }

    private static void addOwner(List<Zone> owners, Zone zone) {
        if (zone != null && !owners.contains(zone)) {
            owners.add(zone);
        }
    }

    private boolean drop(Zone zone, Session session) {
        try {
            zone.drop(session);
            return true;
        } catch (RejectedExecutionException exception) {
            LOGGER.log(Level.WARNING,
                    "Không thể detach Player vì Zone đã dừng: session=" + session.id(), exception);
            return false;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Zone leave failed: session=" + session.id(), exception);
            // Lỗi gửi packet sau khi đã tách không được bỏ qua Zone khác hoặc bản lưu cuối.
            return !zone.hasPlayer(session) && !zone.hasReservation(session)
                    && session.zone() != zone;
        }
    }

    /** Chết rồi thoát game: hồi sinh về nhà trước khi lưu. */
    private static PlayerSaveData captureAfterLeave(Session session) {
        Player player = session.player();
        if (session.state() == SessionState.CLOSED && player.isDead()) {
            player.revive(HOME_MAP_ID, HOME_ZONE_ID, HOME_X, HOME_Y);
        }
        return PlayerSaveData.capture(player);
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private static Zone joinedZone(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return null;
        }
        return session.zone();
    }

    private static boolean canLeave(Session session, Zone source, Player player) {
        return session.state() != SessionState.CLOSED
                && player != null
                && session.zone() == source
                && source.hasPlayer(session);
    }

    private static Trip plan(Zone source, Supplier<Trip> plan) {
        try {
            return source.call(plan);
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    private Zone tripDestination(Session session) {
        Trip trip = trips.get(session);
        return trip == null ? null : trip.destination;
    }

    private static boolean reserve(Zone zone, Session session) {
        try {
            Zone.ReserveStatus status = zone.reserve(session);
            return status == Zone.ReserveStatus.RESERVED
                    || status == Zone.ReserveStatus.ALREADY_RESERVED;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private static void cancel(Zone zone, Session session) {
        try {
            zone.cancel(session);
        } catch (RejectedExecutionException ignored) {
            // Zone đích đã dừng thì không còn chỗ nào được giữ.
        }
    }

    private Zone findZone(int mapId, int zoneId) {
        Map map = findMap(mapId);
        return map == null ? null : map.findZone(zoneId);
    }

    /** Kết quả chuyển map để Handler lưu checkpoint và gửi MAP_INFO. */
    public record MapChange(PlayerSaveData saveData, int zoneId) {
        public MapChange {
            Objects.requireNonNull(saveData, "saveData");
            if (zoneId < 0) {
                throw new IllegalArgumentException("zoneId must be non-negative");
            }
        }
    }
}
