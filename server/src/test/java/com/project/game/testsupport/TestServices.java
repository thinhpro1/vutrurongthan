package com.project.game.testsupport;

import com.project.game.account.AuthService;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import com.project.game.map.MapService;
import com.project.game.map.ZoneRegistry;
import com.project.game.combat.CombatService;
import com.project.game.monster.MonsterFactory;
import com.project.game.monster.MonsterService;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;

import java.util.Map;
import java.util.WeakHashMap;

public final class TestServices {
    private static final Map<AuthService, TestPlayerRepository> PLAYER_REPOSITORIES =
            new WeakHashMap<>();
    private TestServices() { }

    public static AuthService authService() {
        AuthService auth = new AuthService(new TestAccountRepository());
        synchronized (PLAYER_REPOSITORIES) {
            PLAYER_REPOSITORIES.put(auth, new TestPlayerRepository());
        }
        return auth;
    }

    public static SessionServices serverServices() {
        AuthService auth = authService();
        return serverServices(auth, GameResources.unavailable());
    }

    public static SessionServices serverServices(AuthService auth, GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        ZoneRegistry zones = new ZoneRegistry(MapTestSupport.canonicalMaps(), new MonsterFactory(resources));
        MapService maps = new MapService(zones, playerPackets);
        CombatService combat = new CombatService(zones, playerPackets, monsterPackets);
        MonsterService monsters = new MonsterService(zones, monsterPackets, playerPackets);
        return new SessionServices(auth, resources, maps, combat, monsters,
                new PlayerService(playerRepository(auth)));
    }

    public static SessionServices serverServices(AuthService auth, GameResources resources,
                                                GameplayServices gameplay) {
        return new SessionServices(auth, resources, gameplay.mapService(),
                gameplay.combatService(), gameplay.monsterService(),
                new PlayerService(playerRepository(auth)));
    }

    public static SessionServices serverServices(AuthService auth, GameResources resources,
                                                GameplayServices gameplay,
                                                PlayerService players) {
        return new SessionServices(auth, resources, gameplay.mapService(),
                gameplay.combatService(), gameplay.monsterService(), players);
    }

    public static PlayerService playerService(AuthService auth) {
        return new PlayerService(playerRepository(auth));
    }

    private static TestPlayerRepository playerRepository(AuthService auth) {
        synchronized (PLAYER_REPOSITORIES) {
            return PLAYER_REPOSITORIES.computeIfAbsent(auth, ignored -> new TestPlayerRepository());
        }
    }
}
