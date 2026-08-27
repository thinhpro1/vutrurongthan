package com.project.game.service;

import com.project.game.map.MapService;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;

import java.util.Objects;

/** Shared coarse services used by one accepted legacy session. */
public record ServerServices(AuthService auth, ResourceService resources, MapService maps) {
    public ServerServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(maps, "maps");
    }

    public ServerServices(AuthService auth, ResourceService resources) {
        this(
                auth,
                resources,
                new MapService(
                        new PlayerPacketWriter(),
                        new MonsterPacketWriter(),
                        new MonsterRuntimeFactory(resources)));
    }

    public static ServerServices defaults() {
        return new ServerServices(
                new AuthService(),
                ResourceService.unavailable());
    }
}
