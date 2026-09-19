package com.project.game.testsupport;

import com.project.game.account.AuthService;
import com.project.game.player.PlayerService;
import com.project.game.resource.ResourceService;
import com.project.game.service.ServerServices;
import com.project.game.map.MapService;

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

    public static ServerServices serverServices() {
        AuthService auth = authService();
        return serverServices(auth, ResourceService.unavailable());
    }

    public static ServerServices serverServices(AuthService auth, ResourceService resources) {
        return new ServerServices(auth, resources,
                new MapService(
                        new com.project.game.network.packet.PlayerPacketWriter(),
                        new com.project.game.network.packet.MonsterPacketWriter(),
                        new com.project.game.monster.MonsterRuntimeFactory(resources)),
                new PlayerService(playerRepository(auth)));
    }

    public static ServerServices serverServices(AuthService auth, ResourceService resources,
                                                MapService maps) {
        return new ServerServices(auth, resources, maps,
                new PlayerService(playerRepository(auth)));
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
