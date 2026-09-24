package com.project.game.testsupport;

import com.project.game.account.AccountAuth;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import com.project.game.map.MapManager;
import com.project.game.combat.Combat;
import com.project.game.monster.MonsterFactory;
import com.project.game.monster.MonsterManager;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;

import java.util.Map;
import java.util.WeakHashMap;

public final class TestServices {
    private static final Map<AccountAuth, TestPlayerRepository> PLAYER_REPOSITORIES =
            new WeakHashMap<>();
    private TestServices() { }

    public static AccountAuth auth() {
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        synchronized (PLAYER_REPOSITORIES) {
            PLAYER_REPOSITORIES.put(auth, new TestPlayerRepository());
        }
        return auth;
    }

    public static SessionServices serverServices() {
        AccountAuth auth = auth();
        return serverServices(auth, GameResources.unavailable());
    }

    public static SessionServices serverServices(AccountAuth auth, GameResources resources) {
        PlayerPacketWriter playerPackets = new PlayerPacketWriter();
        MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
        MapManager maps = new MapManager(MapTestSupport.canonicalMaps(), new MonsterFactory(resources), playerPackets);
        Combat combat = new Combat(maps, playerPackets, monsterPackets);
        MonsterManager monsterManager = new MonsterManager(maps, monsterPackets, playerPackets);
        return new SessionServices(auth, resources, maps, combat, monsterManager,
                playerRepository(auth));
    }

    public static SessionServices serverServices(AccountAuth auth, GameResources resources,
                                                GameplayServices gameplay) {
        return new SessionServices(auth, resources, gameplay.mapManager(),
                gameplay.combat(), gameplay.monsterManager(),
                playerRepository(auth));
    }

    public static SessionServices serverServices(AccountAuth auth, GameResources resources,
                                                GameplayServices gameplay,
                                                PlayerRepository players) {
        return new SessionServices(auth, resources, gameplay.mapManager(),
                gameplay.combat(), gameplay.monsterManager(), players);
    }

    public static PlayerRepository playerRepositoryFor(AccountAuth auth) {
        return playerRepository(auth);
    }

    private static TestPlayerRepository playerRepository(AccountAuth auth) {
        synchronized (PLAYER_REPOSITORIES) {
            return PLAYER_REPOSITORIES.computeIfAbsent(auth, ignored -> new TestPlayerRepository());
        }
    }
}
