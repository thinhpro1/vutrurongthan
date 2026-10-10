package com.project.game.map;

import com.project.game.monster.MonsterManager;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.service.AreaService;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Giữ các Map public và đưa người chơi đi giữa các map (giống joinMap / teleport của rongthan).
 *
 * <pre>
 * Vào game : enterGame(player)        → Zone.enter → MAP_INFO
 * Đi map   : travel(player, map, x, y) → rời Zone cũ → Zone mới enter → MAP_INFO
 * Thoát    : leave(session)            → rời Zone (hoặc hủy chuyến đi) → bản lưu cuối
 * </pre>
 */
public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    public static final int HOME_MAP_ID = 0;
    public static final int HOME_X = 1250;
    public static final int HOME_Y = 648;

    private final java.util.Map<Integer, MapTemplate> templates;
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
                Map map = new Map(template, monsterManager, area);
                map.setManager(this);
                runtimeMaps.put(mapId, map);
            }
        }
        this.templates = java.util.Map.copyOf(catalog);
        this.maps = Collections.unmodifiableMap(runtimeMaps);
    }

    /** Tìm Map đang mở, không tạo mới. */
    public Map findMap(int mapId) {
        return maps.get(mapId);
    }

    /** Lấy Map đang mở hoặc báo lỗi nếu không có. */
    public Map getMap(int mapId) {
        Map map = findMap(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown or offline map " + mapId);
        }
        return map;
    }

    /** Template của bất kỳ map nào trong catalog (kể cả map không mở). */
    public MapTemplate findTemplate(int mapId) {
        return templates.get(mapId);
    }

    /** Các Map public theo thứ tự id. */
    public List<Map> maps() {
        return List.copyOf(maps.values());
    }

    // ------------------------------------------------------------------
    // Vào game
    // ------------------------------------------------------------------

    /** Sau đăng nhập: đưa Player vào Zone theo vị trí đã lưu; map không mở thì về nhà. */
    public void enterGame(Player player) {
        Map map = findMap(player.mapId());
        if (map == null) {
            map = getMap(HOME_MAP_ID);
            player.changeMap(HOME_MAP_ID, 0, HOME_X, HOME_Y);
        }
        Zone zone = map.findZone(player.zoneId());
        if (zone == null) {
            zone = map.findOrRandomZone();
        }
        player.changeMap(map.id(), zone.zoneId(), player.x(), player.y());
        zone.postEnter(player);
    }

    // ------------------------------------------------------------------
    // Đi sang map khác
    // ------------------------------------------------------------------

    /**
     * Đưa Player sang map khác (waypoint, về nhà, dịch chuyển). Chạy trên thread của Zone hiện
     * tại của Player; không chờ Zone đích. Map đích không mở thì không đi (trả về false).
     */
    public boolean travel(Player player, int mapId, int x, int y) {
        Map map = findMap(mapId);
        if (map == null) {
            return false;
        }
        Zone destination = map.findOrRandomZone();
        // Đánh dấu "đang đi" trước khi rời Zone cũ: lúc nào Player cũng có chỗ để tìm thấy.
        player.startTravel(destination);
        Zone source = player.zone();
        if (source != null) {
            source.leave(player);
        }
        player.changeMap(mapId, destination.zoneId(), x, y);
        saveLater(player);
        destination.postEnter(player);
        return true;
    }

    /** Lưu checkpoint ngoài thread của Zone, theo đúng thứ tự của từng người chơi. */
    private static void saveLater(Player player) {
        Session session = player.session();
        if (session == null) {
            return;
        }
        session.saveLater(PlayerSaveData.capture(player));
    }

    // ------------------------------------------------------------------
    // Thoát game
    // ------------------------------------------------------------------

    /**
     * Thoát game: đuổi theo Player (đang ở Zone nào, hoặc đang đi tới Zone nào) cho tới khi Player
     * không còn ở đâu, rồi mới chụp bản lưu cuối. Chết rồi thoát thì hồi sinh về nhà.
     */
    public PlayerSaveData leave(Session session) {
        Zone.requireOutsideRuntimeWorker("leave");
        if (session == null) {
            return null;
        }
        Player player = session.player();
        if (player == null) {
            return null;
        }
        try {
            Zone zone = currentOrNextZone(player);
            while (zone != null) {
                zone.logout(player);
                zone = currentOrNextZone(player);
            }
        } catch (RejectedExecutionException exception) {
            LOGGER.log(Level.WARNING,
                    "Không thể chụp Player vì Zone đã dừng: session=" + session.id(), exception);
            return null;
        }
        // Không còn Zone nào giữ Player: đọc/sửa ở đây an toàn.
        if (session.state() == SessionState.CLOSED && player.isDead()) {
            player.revive(HOME_MAP_ID, 0, HOME_X, HOME_Y);
        }
        return PlayerSaveData.capture(player);
    }

    private static Zone currentOrNextZone(Player player) {
        Zone zone = player.zone();
        if (zone != null) {
            return zone;
        }
        return player.travelingTo();
    }
}
