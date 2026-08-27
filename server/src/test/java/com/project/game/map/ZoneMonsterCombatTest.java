package com.project.game.map;

import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneMonsterCombatTest {
    @Test
    void ownsLiveMonsterDamageAndDeathState() {
        Zone zone = map1Zone();

        assertTrue(zone.hasLiveMonster(0));
        assertFalse(zone.hasLiveMonster(99));

        var nonLethal = zone.damageMonster(0, 10).orElseThrow();
        assertEquals(0, nonLethal.monsterId());
        assertEquals(10L, nonLethal.damage());
        assertEquals(290L, nonLethal.hpAfter());
        assertFalse(nonLethal.killed());
        assertEquals(290L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(300L, zone.monsterSnapshots().get(1).hp());
        assertEquals(List.of(0, 1, 2, 3, 4, 5),
                zone.monsterSnapshots().stream().map(MonsterSnapshot::id).toList());

        var lethal = zone.damageMonster(0, 300).orElseThrow();
        assertTrue(lethal.killed());
        assertEquals(0L, lethal.hpAfter());
        assertFalse(zone.hasLiveMonster(0));
        assertTrue(zone.damageMonster(0, 10).isEmpty());
    }

    @Test
    void containsRequiresExactSessionIdentity() {
        Zone zone = new Zone(1, 0, List.of());
        Session first = session(PlayerProfile.initial("user01", 7, "alpha1", 1));
        Session equivalent = session(PlayerProfile.initial("user02", 7, "alpha2", 1));

        zone.add(first);

        assertTrue(zone.contains(first));
        assertFalse(zone.contains(equivalent));
        assertFalse(zone.contains(null));
    }

    private static Zone map1Zone() {
        MonsterRuntimeFactory factory = new MonsterRuntimeFactory(
                ResourceService.fromFrameRoot(Path.of("resources", "json")));
        return new Zone(1, 0, factory.createForMap(1));
    }

    private static Session session(PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                ServerServices.defaults(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        session.bindPlayer(player);
        return session;
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() { return input; }

        @Override
        public OutputStream output() { return output; }

        @Override
        public String remoteAddress() { return "zone-monster-test"; }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}
