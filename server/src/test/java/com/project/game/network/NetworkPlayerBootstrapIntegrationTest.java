package com.project.game.network;

import com.project.game.testsupport.TestServices;

import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.NetworkIntegrationTestSupport.*;

class NetworkPlayerBootstrapIntegrationTest {

    @Test
    void javaClientCreatesFreshPlayerAndParsesLegacyMapZero() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources), null,
                NetworkConfig.defaults());
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Throwable failure) {
                serverFailure.set(failure);
            }
        });
        try {
            waitForPort(server);
            EnterGameResponses earth = runCreatePlayer(server.localPort(), "user01", "alpha1", 0);
            assertFreshPlayer(earth.playerInfo(), "alpha1", 0, 5, 6,
                    List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36));
            assertEquals(List.of(new ParsedPaint("50.0", 0), new ParsedPaint("100.0", 1)),
                    earth.playerInfo().paints().get(0));
            assertEquals(List.of(new ParsedPaint("10.0", 5), new ParsedPaint("20.0", 17),
                            new ParsedPaint("30.0", 25)),
                    earth.playerInfo().paints().get(31));
            assertLegacyMapZero(earth.mapInfo());
            waitForNoSessions(server);

            EnterGameResponses namek = runCreatePlayer(server.localPort(), "user02", "beta22", 1);
            assertFreshPlayer(namek.playerInfo(), "beta22", 1, 3, 7,
                    List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36));
            assertLegacyMapZero(namek.mapInfo());
            waitForNoSessions(server);

            EnterGameResponses saiyan = runCreatePlayer(server.localPort(), "user03", "gamma3", 2);
            assertFreshPlayer(saiyan.playerInfo(), "gamma3", 2, 4, 8,
                    List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36));
            assertLegacyMapZero(saiyan.mapInfo());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during create-player MAP_INFO integration test");
    }

    @Test
    void javaClientRelogsExistingPlayerAndReceivesLegacyMapZero() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources), null,
                NetworkConfig.defaults());
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Throwable failure) {
                serverFailure.set(failure);
            }
        });
        try {
            waitForPort(server);
            EnterGameResponses created = runCreatePlayer(server.localPort(), "relog01", "relog", 0);
            assertLegacyMapZero(created.mapInfo());
            waitForNoSessions(server);

            EnterGameResponses relogged = runLoginExistingPlayer(
                    server.localPort(), "relog01", "secret1");
            assertEquals("relog", relogged.playerInfo().name());
            assertLegacyMapZero(relogged.mapInfo());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during existing-player MAP_INFO integration test");
    }

    private static void assertFreshPlayer(ParsedPlayerInfo player, String name, int gender,
                                          int head, int body, List<Integer> skillIds) {
        assertTrue(player.id() > 0);
        assertEquals(name, player.name());
        assertEquals(gender, player.gender());
        assertEquals(head, player.head());
        assertEquals(body, player.body());
        assertEquals(10, player.baseDamage());
        assertEquals(200, player.baseHp());
        assertEquals(200, player.baseMp());
        assertEquals(5, player.baseConstitution());
        assertEquals(200, player.maxHp());
        assertEquals(200, player.maxMp());
        assertEquals(200, player.hp());
        assertEquals(200, player.mp());
        assertEquals(skillIds, player.skillIds());
        assertEquals(List.of(gender, -1, -1, -1, -1, -1), player.keySkillIds());
        assertEquals(gender, player.mySkillId());
    }

    private static void assertLegacyMapZero(ParsedMapInfo map) {
        assertEquals(0, map.mapId());
        assertEquals(0, map.iconId());
        assertEquals("Núi Paozu", map.name());
        assertEquals(20, map.row());
        assertEquals(62, map.column());
        assertEquals(1240, map.data().length());
        assertTrue(map.data().chars().allMatch(ch -> ch == '0' || ch == '1'));
        assertEquals(List.of(51, 52, 53), map.imagesBgr());
        assertEquals(List.of(
                List.of(128, 213, 242),
                List.of(141, 185, 128),
                List.of(90, 154, 64),
                List.of(69, 153, 51)
        ), map.colorsBgr());
        assertFalse(map.line());
        assertNull(map.dataLine());
        assertEquals(0, map.zoneId());
        assertEquals(1250, map.x());
        assertEquals(648, map.y());
        assertEquals(1, map.waypoints().size());
        assertEquals(new ParsedWaypoint(4464, 936, 1, "Bờ sông Pu"),
                map.waypoints().getFirst());
        assertEquals(0, map.npcCount());
        assertTrue(map.monsters().isEmpty());
        assertEquals(0, map.itemMapCount());
        assertFalse(map.dragonActive());
        assertEquals(0, map.remaining());
    }
}
