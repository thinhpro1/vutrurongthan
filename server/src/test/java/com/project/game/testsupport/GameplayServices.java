package com.project.game.testsupport;

import com.project.game.combat.Combat;
import com.project.game.map.MapManager;
import com.project.game.map.MapTemplate;
import com.project.game.map.Zone;
import com.project.game.monster.MonsterFactory;
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
                            MonsterFactory monsterFactory) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterFactory, playerPackets, monsterPackets),
                playerPackets, monsterPackets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterFactory monsterFactory,
                            Clock clock) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterFactory, playerPackets, monsterPackets),
                playerPackets, monsterPackets, clock, RandomGenerator.getDefault());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets,
                            MonsterFactory monsterFactory,
                            Clock clock,
                            RandomGenerator random) {
        this(runtime(MapTestSupport.canonicalMaps(), monsterFactory, playerPackets, monsterPackets),
                playerPackets, monsterPackets, clock, random);
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
        initialize(runtime(catalog, new MonsterFactory(resources), playerPackets, monsterPackets),
                playerPackets, monsterPackets, clock, random);
    }

    public GameplayServices(Map<Integer, MapTemplate> maps, GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        initialize(runtime(maps, new MonsterFactory(resources), playerPackets, monsterPackets),
                playerPackets, monsterPackets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    private GameplayServices(Runtime runtime, PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets, Clock clock,
                             RandomGenerator random) {
        initialize(runtime, playerPackets, monsterPackets, clock, random);
    }

    private void initialize(Runtime runtime, PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets, Clock clock,
                            RandomGenerator random) {
        maps = runtime.maps();
        combat = new Combat(runtime.area(), playerPackets, clock);
        monsterManager = new MonsterManager(maps, clock, random);
    }

    private static Runtime runtime(Map<Integer, MapTemplate> catalog,
                                   MonsterFactory monsterFactory,
                                   PlayerPacketWriter playerPackets,
                                   MonsterPacketWriter monsterPackets) {
        AreaService area = new AreaService(playerPackets, monsterPackets);
        return new Runtime(new MapManager(catalog, monsterFactory, area), area);
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
    public void tickMonsterLifecycle() { monsterManager.update(); }
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
