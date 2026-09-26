package com.project.game.testsupport;

import com.project.game.combat.Combat;
import com.project.game.map.MapManager;
import com.project.game.map.MapTemplate;
import com.project.game.map.Zone;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Test-only composition of public Map, Zone combat, and lifecycle services. */
public final class GameplayServices {
    private MapManager maps;
    private Combat combat;
    private MonsterManager monsterManager;

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterManager monsterManager) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterManager, playerPackets, monsterPackets),
                playerPackets, monsterPackets, monsterManager,
                Clock.systemUTC());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterManager monsterManager,
                            Clock clock) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterManager, playerPackets, monsterPackets),
                playerPackets, monsterPackets, monsterManager,
                clock);
    }

    public GameplayServices(GameResources resources) {
        this(resources, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public GameplayServices(GameResources resources, Clock clock) {
        this(resources, clock, RandomGenerator.getDefault());
    }

    public GameplayServices(GameResources resources, Clock clock, RandomGenerator random) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        Map<Integer, MapTemplate> catalog = resources.maps().isEmpty()
                ? MapTestSupport.canonicalMaps() : resources.maps();
        MonsterManager monsterManager = new MonsterManager(resources, clock, random);
        initialize(runtime(catalog, monsterManager, playerPackets, monsterPackets),
                playerPackets, monsterPackets, monsterManager, clock);
    }

    public GameplayServices(Map<Integer, MapTemplate> maps, GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        MonsterManager monsterManager = new MonsterManager(resources);
        initialize(runtime(maps, monsterManager, playerPackets, monsterPackets),
                playerPackets, monsterPackets, monsterManager,
                Clock.systemUTC());
    }

    private GameplayServices(Runtime runtime, PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets,
                             MonsterManager monsterManager, Clock clock) {
        initialize(runtime, playerPackets, monsterPackets, monsterManager, clock);
    }

    private void initialize(Runtime runtime, PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterManager monsterManager, Clock clock) {
        maps = runtime.maps();
        combat = new Combat(runtime.area(), playerPackets, clock);
        this.monsterManager = monsterManager;
    }

    private static Runtime runtime(Map<Integer, MapTemplate> catalog,
                                   MonsterManager monsterManager,
                                   PlayerPacketWriter playerPackets,
                                   MonsterPacketWriter monsterPackets) {
        AreaService area = new AreaService(playerPackets, monsterPackets);
        return new Runtime(new MapManager(catalog, monsterManager, area), area);
    }

    public MapManager mapManager() {
        return maps;
    }

    public Combat combat() {
        return combat;
    }

    public MonsterManager monsterManager() {
        return monsterManager;
    }

    public MapManager maps() {
        return maps;
    }

    public void finishLoad(Session session) { maps.finishLoad(session); }
    public void leave(Session session) { maps.leave(session); }
    public boolean returnTownFromDeath(Session session) {
        return maps.returnTownFromDeath(session) != null;
    }
    public boolean changeMap(Session session) {
        return maps.changeMap(session) != null;
    }
    public boolean movePlayer(Session session, int x, int y) {
        Zone zone = session == null ? null : session.zone();
        return zone != null && zone.move(session, x, y);
    }
    public int memberCount(int mapId, int zoneId) {
        com.project.game.map.Map map = maps.findMap(mapId);
        if (map == null) {
            return 0;
        }
        Zone zone = map.findZone(zoneId);
        return zone == null ? 0 : zone.size();
    }
    public boolean canTargetMonster(Session session, int monsterId) {
        return combat.canTargetMonster(session, monsterId);
    }
    public boolean attackMonster(Session session, int monsterId) {
        return combat.attackMonster(session, monsterId);
    }
    public void tickMonsterLifecycle() { monsterManager.update(maps); }
    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        Zone zone = maps.getMap(mapId).findZone(zoneId);
        if (zone == null) {
            throw new IllegalArgumentException("unknown zone " + mapId + "/" + zoneId);
        }
        return zone.monsterSnapshots();
    }
    public Zone findZone(int mapId, int zoneId) {
        com.project.game.map.Map map = maps.findMap(mapId);
        return map == null ? null : map.findZone(zoneId);
    }

    private record Runtime(MapManager maps, AreaService area) {
    }
}
