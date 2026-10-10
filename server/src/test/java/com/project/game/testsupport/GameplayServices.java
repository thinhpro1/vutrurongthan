package com.project.game.testsupport;

import com.project.game.map.MapManager;
import com.project.game.map.MapTemplate;
import com.project.game.map.Zone;
import com.project.game.map.ZoneTestHooks;
import com.project.game.monster.MonsterManager;
import com.project.game.player.Player;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Test-only composition of public Map, Zone combat, and lifecycle services. */
public final class GameplayServices {
    private MapManager maps;
    private Clock clock;
    private MonsterManager monsterManager;

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterManager monsterManager) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterManager, playerPackets, monsterPackets),
                monsterManager, Clock.systemUTC());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterManager monsterManager,
                            Clock clock) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterManager, playerPackets, monsterPackets),
                monsterManager, clock);
    }

    public GameplayServices(GameResources resources) {
        this(resources, Clock.systemUTC(), new java.util.Random());
    }

    public GameplayServices(GameResources resources, Clock clock) {
        this(resources, clock, new java.util.Random());
    }

    public GameplayServices(GameResources resources, Clock clock, java.util.Random random) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        Map<Integer, MapTemplate> catalog = resources.maps().isEmpty()
                ? MapTestSupport.canonicalMaps() : resources.maps();
        MonsterManager monsterManager = new MonsterManager(resources, clock, random);
        initialize(runtime(catalog, monsterManager, playerPackets, monsterPackets),
                monsterManager, clock);
    }

    public GameplayServices(Map<Integer, MapTemplate> maps, GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        MonsterManager monsterManager = new MonsterManager(resources);
        initialize(runtime(maps, monsterManager, playerPackets, monsterPackets),
                monsterManager, Clock.systemUTC());
    }

    private GameplayServices(Runtime runtime, MonsterManager monsterManager, Clock clock) {
        initialize(runtime, monsterManager, clock);
    }

    private void initialize(Runtime runtime, MonsterManager monsterManager, Clock clock) {
        maps = runtime.maps();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.monsterManager = monsterManager;
    }

    private static Runtime runtime(Map<Integer, MapTemplate> catalog,
                                   MonsterManager monsterManager,
                                   PlayerPacketWriter playerPackets,
                                   MonsterPacketWriter monsterPackets) {
        AreaService area = new AreaService(playerPackets, monsterPackets);
        return new Runtime(new MapManager(catalog, monsterManager, area));
    }

    public MapManager mapManager() {
        return maps;
    }

    public Clock clock() {
        return clock;
    }

    public MonsterManager monsterManager() {
        return monsterManager;
    }

    public MapManager maps() {
        return maps;
    }

    /** Đăng nhập xong: vào Zone theo vị trí của Player và tải map xong. */
    public boolean finishLoad(Session session) {
        return ZoneTestHooks.joinGame(maps, session);
    }
    public void leave(Session session) { maps.leave(session); }
    /** RETURN_TOWN_FROM_DIE và chờ tới nhà; false nếu Player không chết hoặc không ở Zone. */
    public boolean returnHomeFromDeath(Session session) {
        Player player = session.player();
        Zone zone = player.zone();
        if (zone == null) {
            return false;
        }
        boolean done = ZoneTestHooks.run(zone, player, () -> {
            if (!player.isDead()) {
                return false;
            }
            player.returnTownFromDead();
            return true;
        });
        ZoneTestHooks.arrive(player);
        return done;
    }
    /** REQUEST_CHANGE_MAP và chờ tới map mới; false nếu không đứng trên waypoint. */
    public boolean changeMap(Session session) {
        Player player = session.player();
        Zone zone = player.zone();
        if (zone == null) {
            return false;
        }
        boolean moved = ZoneTestHooks.run(zone, player, () -> {
            player.requestChangeMap();
            return player.zone() != zone;
        });
        ZoneTestHooks.arrive(player);
        return moved;
    }
    public boolean movePlayer(Session session, int x, int y) {
        Zone zone = session == null ? null : session.zone();
        return zone != null && ZoneTestHooks.move(zone, session, x, y);
    }
    public int memberCount(int mapId, int zoneId) {
        com.project.game.map.Map map = maps.findMap(mapId);
        if (map == null) {
            return 0;
        }
        Zone zone = map.findZone(zoneId);
        if (zone == null) {
            return 0;
        }
        ZoneTestHooks.drain(zone);
        return zone.playerCount();
    }
    public boolean canTargetMonster(Session session, int monsterId) {
        Zone zone = session == null ? null : session.zone();
        if (zone == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        return ZoneTestHooks.useSkill(zone, session, monsterId);
    }
    public boolean attackMonster(Session session, int monsterId) {
        Zone zone = session == null ? null : session.zone();
        if (zone == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        return ZoneTestHooks.attackMonster(zone, session, monsterId, clock.millis());
    }
    public void tickMonsterLifecycle() { monsterManager.update(maps); }
    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        Zone zone = maps.getMap(mapId).findZone(zoneId);
        if (zone == null) {
            throw new IllegalArgumentException("unknown zone " + mapId + "/" + zoneId);
        }
        return ZoneTestHooks.monsterSnapshots(zone); // chụp sau mọi lệnh đã xếp trước đó
    }
    public Zone findZone(int mapId, int zoneId) {
        com.project.game.map.Map map = maps.findMap(mapId);
        return map == null ? null : map.findZone(zoneId);
    }

    private record Runtime(MapManager maps) {
    }
}
