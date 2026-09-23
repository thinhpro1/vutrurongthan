package com.project.game.testsupport;

import com.project.game.combat.CombatService;
import com.project.game.map.MapService;
import com.project.game.map.MapTemplate;
import com.project.game.map.Zone;
import com.project.game.map.ZoneRegistry;
import com.project.game.monster.MonsterFactory;
import com.project.game.monster.MonsterManager;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.resource.GameResources;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Test-only composition of the three gameplay services over one ZoneRegistry. */
public final class GameplayServices {
    private ZoneRegistry zones;
    private MapService maps;
    private CombatService combat;
    private MonsterManager monsterManager;

    public GameplayServices(PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets,
                             MonsterFactory monsterFactory) {
        this(new ZoneRegistry(MapTestSupport.canonicalMaps(), monsterFactory), playerPackets, monsterPackets,
                Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets,
                             MonsterFactory monsterFactory,
                             Clock clock) {
        this(new ZoneRegistry(MapTestSupport.canonicalMaps(), monsterFactory), playerPackets, monsterPackets,
                clock, RandomGenerator.getDefault());
    }

    public GameplayServices(PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets,
                             MonsterFactory monsterFactory,
                             Clock clock,
                             RandomGenerator random) {
        this(new ZoneRegistry(MapTestSupport.canonicalMaps(), monsterFactory), playerPackets,
                monsterPackets, clock, random);
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
        Map<Integer, MapTemplate> maps = resources.maps().isEmpty()
                ? MapTestSupport.canonicalMaps()
                : resources.maps();
        initialize(new ZoneRegistry(maps, new MonsterFactory(resources)), playerPackets,
                monsterPackets, clock, random);
    }

    public GameplayServices(Map<Integer, MapTemplate> maps,
                            GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        initialize(new ZoneRegistry(maps, new MonsterFactory(resources)), playerPackets,
                monsterPackets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    private GameplayServices(ZoneRegistry zones, PlayerPacketWriter playerPackets,
                             MonsterPacketWriter monsterPackets, Clock clock,
                             RandomGenerator random) {
        initialize(zones, playerPackets, monsterPackets, clock, random);
    }

    private void initialize(ZoneRegistry registry, PlayerPacketWriter playerPackets,
                            MonsterPacketWriter monsterPackets, Clock clock,
                            RandomGenerator random) {
        this.zones = registry;
        this.maps = new MapService(registry, playerPackets);
        this.combat = new CombatService(registry, playerPackets, monsterPackets, clock);
        this.monsterManager = new MonsterManager(registry, monsterPackets, playerPackets, clock, random);
    }

    public MapService mapService() {
        return maps;
    }

    public CombatService combatService() {
        return combat;
    }

    public MonsterManager monsterManager() {
        return monsterManager;
    }

    public ZoneRegistry zones() {
        return zones;
    }

    public void finishLoad(Session session) { maps.finishLoad(session); }
    public void leave(Session session) { maps.leave(session); }
    public Optional<PlayerProfile> returnTownFromDeath(Session session) {
        return maps.returnTownFromDeath(session);
    }
    public Optional<PlayerProfile> changeMap(Session session, int expectedMapId, int expectedZoneId,
                                             int destinationMapId, int destinationZoneId,
                                             int destinationX, int destinationY) {
        return maps.changeMap(session, expectedMapId, expectedZoneId, destinationMapId,
                destinationZoneId, destinationX, destinationY);
    }
    public boolean movePlayer(Session session, int x, int y) { return maps.movePlayer(session, x, y); }
    public int memberCount(int mapId, int zoneId) { return maps.memberCount(mapId, zoneId); }
    public boolean canTargetMonster(Session session, int monsterId) {
        return combat.canTargetMonster(session, monsterId);
    }
    public boolean attackMonster(Session session, int monsterId, long damage) {
        return combat.attackMonster(session, monsterId, damage);
    }
    public void tickMonsterLifecycle() { monsterManager.update(); }
    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        return monsterManager.monsterSnapshots(mapId, zoneId);
    }
    public Zone findZone(int mapId, int zoneId) { return zones.find(mapId, zoneId); }
}
