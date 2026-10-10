package com.project.game.testsupport;

import com.project.game.map.Map;
import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterManager;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;

/** Map rỗng (không tự tạo Zone) để test dựng Zone riêng với Monster/sức chứa tùy ý. */
public final class TestMaps {
    private TestMaps() {
    }

    public static Map emptyMap(int mapId, int maxPlayer) {
        MapTemplate canonical = MapTestSupport.canonicalMaps().get(mapId);
        MapTemplate template = new MapTemplate(canonical.id(), canonical.name(), "ONLINE",
                canonical.planet(), 0, canonical.maxZone(), maxPlayer, canonical.dataId(),
                canonical.data(), canonical.waypoints());
        return new Map(template, new MonsterManager(GameResources.unavailable()), area());
    }

    public static AreaService area() {
        return new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
    }
}
