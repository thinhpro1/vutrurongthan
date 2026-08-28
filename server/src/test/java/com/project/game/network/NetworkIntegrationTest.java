package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.frame.FrameTemplate;
import com.project.game.map.MapService;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import com.project.game.test.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkIntegrationTest {
    @Test
    void livingPlayerReturnTownRequestIsIgnored() throws Exception {
        String accountName = "livingreturn";
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        assertTrue(auth.register(accountName, "secret1").success());
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient client = LivePlayerClient.create(
                    server.localPort(), accountName, "returner", 0)) {
                client.finishLoadMap();
                Session live = server.sessions().findByAccount(accountName);
                assertTrue(live != null);
                var original = live.player();

                client.returnTownFromDie();
                assertNoServerMessage(client);

                Session unchanged = server.sessions().findByAccount(accountName);
                assertTrue(unchanged != null);
                assertEquals(original, unchanged.player());
                assertTrue(unchanged.state() != SessionState.CLOSED);
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during living return-town test");
    }

    @Test
    void javaClientRoundTripsMap0AndMap1WithCachedTemplates() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        assertTrue(auth.register("mapround1", "secret1").success());
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient client = LivePlayerClient.create(
                    server.localPort(), "mapround1", "round", 0)) {
                client.finishLoadMap();
                client.move(4464, 936);
                client.requestChangeMap();
                ParsedMapInfo map1 = client.readMapInfo();
                assertEquals(1, map1.mapId());
                assertEquals(90, map1.x());
                assertEquals(1008, map1.y());
                assertEquals(List.of(new ParsedWaypoint(0, 1008, 0, "Núi Paozu")),
                        map1.waypoints());
                assertEquals(0, map1.npcCount());
                assertEquals(canonicalMap1Monsters(), map1.monsters());
                assertEquals(0, map1.itemMapCount());
                assertFalse(map1.dragonActive());
                client.finishLoadMap();

                client.move(20, 1008);
                client.requestChangeMap();
                ParsedMapInfo map0 = client.readMapInfo();
                assertEquals(0, map0.mapId());
                assertEquals(4374, map0.x());
                assertEquals(936, map0.y());
                assertEquals(List.of(new ParsedWaypoint(4464, 936, 1, "Bờ sông Pu")),
                        map0.waypoints());
                assertTrue(map0.name() == null, "cached Map0 packet must omit static template");
                assertTrue(map0.monsters().isEmpty());
                assertEquals(0, map0.remaining());
                client.finishLoadMap();

                client.move(4464, 936);
                client.requestChangeMap();
                ParsedMapInfo secondMap1 = client.readMapInfo();
                assertEquals(1, secondMap1.mapId());
                assertTrue(secondMap1.name() == null,
                        "cached Map1 packet must omit static template");
                assertEquals(90, secondMap1.x());
                assertEquals(1008, secondMap1.y());
                assertEquals(canonicalMap1Monsters(), secondMap1.monsters());
                assertEquals(0, secondMap1.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during map round-trip test");
    }

    @Test
    void javaClientsFollowEachOtherAcrossMapsWithoutCrossMapPresence() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        assertTrue(auth.register("mapzonea", "secret1").success());
        assertTrue(auth.register("mapzoneb", "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient first = LivePlayerClient.create(
                    server.localPort(), "mapzonea", "alpha1", 0);
                 LivePlayerClient second = LivePlayerClient.create(
                         server.localPort(), "mapzoneb", "beta22", 1)) {
                first.finishLoadMap();
                second.finishLoadMap();
                assertAddPlayer(second.readServerMessage(), first.playerInfo().id(), "alpha1", 0);
                assertAddPlayer(first.readServerMessage(), second.playerInfo().id(), "beta22", 1);

                first.move(4464, 936);
                assertEquals(MessageName.PLAYER_MOVE, second.readServerMessage().command());
                first.requestChangeMap();
                ParsedMapInfo firstMap1 = first.readMapInfo();
                assertEquals(1, firstMap1.mapId());
                assertEquals(canonicalMap1Monsters(), firstMap1.monsters());
                assertEquals(0, maps.memberCount(1, 0));
                assertEquals(MessageName.REMOVE_PLAYER, second.readServerMessage().command());
                first.finishLoadMap();

                first.move(120, 1000);
                assertNoServerMessage(second);
                second.move(1260, 640);
                assertNoServerMessage(first);

                second.move(4464, 936);
                second.requestChangeMap();
                ParsedMapInfo secondMap1 = second.readMapInfo();
                assertEquals(1, secondMap1.mapId());
                assertEquals(canonicalMap1Monsters(), secondMap1.monsters());
                assertEquals(1, maps.memberCount(1, 0));
                second.finishLoadMap();
                assertAddPlayerId(first.readServerMessage(), second.playerInfo().id());
                assertAddPlayerId(second.readServerMessage(), first.playerInfo().id());
                second.finishLoadMap();
                assertNoServerMessage(first);
                assertNoServerMessage(second);

                first.move(150, 1000);
                Message movement = second.readServerMessage();
                assertEquals(MessageName.PLAYER_MOVE, movement.command());
                var movementReader = movement.reader();
                assertEquals(first.playerInfo().id(), movementReader.readInt());
                assertEquals(150, movementReader.readShort());
                assertEquals(1000, movementReader.readShort());
                assertEquals(0, movementReader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during cross-map test");
    }

    @Test
    void twoClientsSeeSameZonePresenceMovementAndDisconnect() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        assertTrue(auth.register("zonea1", "secret1").success());
        assertTrue(auth.register("zoneb1", "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Exception | Error failure) {
                serverFailure.set(failure);
            }
        });

        try {
            waitForPort(server);
            try (LivePlayerClient first = LivePlayerClient.create(server.localPort(), "zonea1", "alpha1", 0);
                 LivePlayerClient second = LivePlayerClient.create(server.localPort(), "zoneb1", "beta22", 1)) {
                first.finishLoadMap();
                second.finishLoadMap();

                assertAddPlayer(second.readServerMessage(), first.playerInfo().id(), "alpha1", 0);
                assertAddPlayer(first.readServerMessage(), second.playerInfo().id(), "beta22", 1);
                assertEquals(2, maps.memberCount(0, 0));

                second.move(1260, 640);
                Message movement = first.readServerMessage();
                assertEquals(MessageName.PLAYER_MOVE, movement.command());
                var movementReader = movement.reader();
                assertEquals(second.playerInfo().id(), movementReader.readInt());
                assertEquals(1260, movementReader.readShort());
                assertEquals(640, movementReader.readShort());
                assertEquals(0, movementReader.remaining());
                assertNoServerMessage(second);

                second.close();
                Message removed = first.readServerMessage();
                assertEquals(MessageName.REMOVE_PLAYER, removed.command());
                var removeReader = removed.reader();
                assertEquals(second.playerInfo().id(), removeReader.readInt());
                assertEquals(0, removeReader.remaining());
                assertEquals(1, maps.memberCount(0, 0));
                assertTrue(server.sessions().findByAccount("zonea1") != null);
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during two-client presence integration test");
    }

    @Test
    void twoClientsFightMap1MonsterObserveRespawnAndFightAgain() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("combatza", "secret1").success());
        assertTrue(auth.register("combatzb", "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources),
                clock);
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient first = LivePlayerClient.create(
                    server.localPort(), "combatza", "alpha1", 0);
                 LivePlayerClient second = LivePlayerClient.create(
                         server.localPort(), "combatzb", "beta22", 1)) {
                first.finishLoadMap();
                second.finishLoadMap();
                assertAddPlayer(second.readServerMessage(), first.playerInfo().id(), "alpha1", 0);
                assertAddPlayer(first.readServerMessage(), second.playerInfo().id(), "beta22", 1);

                first.move(4464, 936);
                first.requestChangeMap();
                ParsedMapInfo firstMap1 = first.readMapInfo();
                assertEquals(canonicalMap1Monsters(), firstMap1.monsters());
                first.finishLoadMap();
                assertEquals(MessageName.PLAYER_MOVE, second.readServerMessage().command());
                assertEquals(MessageName.REMOVE_PLAYER, second.readServerMessage().command());

                second.move(4464, 936);
                second.requestChangeMap();
                ParsedMapInfo secondMap1 = second.readMapInfo();
                assertEquals(canonicalMap1Monsters(), secondMap1.monsters());
                second.finishLoadMap();
                assertAddPlayerId(first.readServerMessage(), second.playerInfo().id());
                assertAddPlayerId(second.readServerMessage(), first.playerInfo().id());

                // Keep this respawn-wire regression focused on monster lifecycle packets;
                // the retaliation suite below covers the in-range attack path.
                first.move(3_000, 3_000);
                assertEquals(MessageName.PLAYER_MOVE, second.readServerMessage().command());

                first.prepareMonsterAttack(0, 0);
                first.impactMonster(0);
                assertMonsterInjure(first.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(second.readServerMessage(), 0, 10, 290);
                assertEquals(290L, maps.monsterSnapshots(1, 0).getFirst().hp());

                for (int expectedHp = 280; expectedHp >= 10; expectedHp -= 10) {
                    first.prepareMonsterAttack(0, 0);
                    first.impactMonster(0);
                    assertMonsterInjure(first.readServerMessage(), 0, 10, expectedHp);
                    assertMonsterInjure(second.readServerMessage(), 0, 10, expectedHp);
                }

                first.prepareMonsterAttack(0, 0);
                first.impactMonster(0);
                assertMonsterDeath(first.readServerMessage(), 0, 10);
                assertPotentialReward(first.readServerMessage(), 11L);
                assertMonsterDeath(second.readServerMessage(), 0, 10);
                assertEquals(0L, maps.monsterSnapshots(1, 0).getFirst().hp());
                assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());

                first.prepareMonsterAttack(0, 0);
                first.impactMonster(0);
                assertNoServerMessage(first);
                assertNoServerMessage(second);

                clock.advanceMillis(8_000L);
                assertNoServerMessage(first);
                assertNoServerMessage(second);

                clock.advanceMillis(1L);
                assertMonsterRespawn(first.readServerMessage(), 0, 0, 300L);
                assertMonsterRespawn(second.readServerMessage(), 0, 0, 300L);
                var respawned = maps.monsterSnapshots(1, 0).getFirst();
                assertEquals(0, respawned.id());
                assertEquals(300L, respawned.hp());
                assertEquals(300L, respawned.maxHp());
                assertEquals(0, respawned.status());
                assertEquals(975, respawned.x());
                assertEquals(936, respawned.y());

                first.prepareMonsterAttack(0, 0);
                first.impactMonster(0);
                assertMonsterInjure(first.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(second.readServerMessage(), 0, 10, 290);
                assertEquals(290L, maps.monsterSnapshots(1, 0).getFirst().hp());

                second.close();
                assertEquals(MessageName.REMOVE_PLAYER, first.readServerMessage().command());

                first.move(0, 1008);
                first.requestChangeMap();
                ParsedMapInfo map0 = first.readMapInfo();
                assertEquals(0, map0.mapId());
                first.finishLoadMap();

                first.move(4464, 936);
                first.requestChangeMap();
                ParsedMapInfo revisitedMap1 = first.readMapInfo();
                assertEquals(1, revisitedMap1.mapId());
                assertEquals(290L, revisitedMap1.monsters().getFirst().hp());
                assertEquals(0, revisitedMap1.monsters().getFirst().status());
                assertTrue(revisitedMap1.monsters().stream()
                        .skip(1)
                        .allMatch(monster -> monster.hp() == 300L && monster.status() == 0));
                first.finishLoadMap();
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during combat test");
    }

    @Test
    void javaClientReceivesPotentialRewardAfterKillingMonster() throws Exception {
        String accountName = "rewardtcp";
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        assertTrue(auth.register(accountName, "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient client = LivePlayerClient.create(
                    server.localPort(), accountName, "rewarder", 0)) {
                client.finishLoadMap();
                client.move(4464, 936);
                client.requestChangeMap();
                ParsedMapInfo map1 = client.readMapInfo();
                assertEquals(1, map1.mapId());
                client.finishLoadMap();
                client.move(2_000, 1_008);
                awaitPlayerPosition(server, accountName, 2_000, 1_008);

                Session live = server.sessions().findByAccount(accountName);
                assertTrue(live != null);
                assertEquals(1L, live.player().potential());
                assertEquals(1L, live.player().power());

                for (int expectedHp = 290; expectedHp >= 10; expectedHp -= 10) {
                    client.prepareMonsterAttack(0, 0);
                    client.impactMonster(0);
                    assertMonsterInjure(client.readServerMessage(), 0, 10, expectedHp);
                }

                live = server.sessions().findByAccount(accountName);
                assertTrue(live != null);
                assertEquals(100L, live.player().hp());
                assertEquals(1L, live.player().potential());
                assertEquals(1L, live.player().power());

                client.prepareMonsterAttack(0, 0);
                client.impactMonster(0);
                assertMonsterDeath(client.readServerMessage(), 0, 10);
                assertPotentialReward(client.readServerMessage(), 11L);

                live = server.sessions().findByAccount(accountName);
                assertTrue(live != null);
                assertEquals(11L, live.player().potential());
                assertEquals(1L, live.player().power());

                sendMove(client.codec, client.transport, client.cipher, 1260, 640);
                awaitPlayerPosition(server, accountName, 1260, 640);
                live = server.sessions().findByAccount(accountName);
                assertTrue(live != null);
                assertEquals(11L, live.player().potential());
                assertEquals(1L, live.player().power());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during monster reward integration test");
    }

    @Test
    void monsterRetaliatesAfterHitWithoutLethalPlayerDamage() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("retaliatea", "secret1").success());
        assertTrue(auth.register("retaliateb", "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources),
                clock,
                new Random(12345L));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient first = LivePlayerClient.create(
                    server.localPort(), "retaliatea", "alpha1", 0);
                 LivePlayerClient second = LivePlayerClient.create(
                         server.localPort(), "retaliateb", "beta22", 1)) {
                first.finishLoadMap();
                second.finishLoadMap();
                assertAddPlayer(second.readServerMessage(), first.playerInfo().id(), "alpha1", 0);
                assertAddPlayer(first.readServerMessage(), second.playerInfo().id(), "beta22", 1);

                first.move(4464, 936);
                first.requestChangeMap();
                assertEquals(1, first.readMapInfo().mapId());
                first.finishLoadMap();
                assertEquals(MessageName.PLAYER_MOVE, second.readServerMessage().command());
                assertEquals(MessageName.REMOVE_PLAYER, second.readServerMessage().command());

                second.move(4464, 936);
                second.requestChangeMap();
                assertEquals(1, second.readMapInfo().mapId());
                second.finishLoadMap();
                assertAddPlayerId(first.readServerMessage(), second.playerInfo().id());
                assertAddPlayerId(second.readServerMessage(), first.playerInfo().id());
                assertNoServerMessage(first);
                assertNoServerMessage(second);

                first.prepareMonsterAttack(0, 0);
                first.impactMonster(0);
                assertMonsterInjure(first.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(second.readServerMessage(), 0, 10, 290);
                clock.advanceMillis(1L);
                assertMonsterAttack(first.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertMonsterAttack(second.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertEquals(90L, server.sessions().findByAccount("retaliatea").player().hp());

                clock.advanceMillis(1_600L);
                assertNoServerMessage(first);
                assertNoServerMessage(second);
                clock.advanceMillis(1L);
                assertMonsterAttack(first.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertMonsterAttack(second.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertEquals(80L, server.sessions().findByAccount("retaliatea").player().hp());

                for (int expectedHp = 70; expectedHp >= 10; expectedHp -= 10) {
                    clock.advanceMillis(1_601L);
                    assertMonsterAttack(first.readServerMessage(), 0, first.playerInfo().id(), 10L);
                    assertMonsterAttack(second.readServerMessage(), 0, first.playerInfo().id(), 10L);
                    assertEquals(expectedHp,
                            server.sessions().findByAccount("retaliatea").player().hp());
                }
                clock.advanceMillis(1_601L);
                assertMonsterAttack(first.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertMeDie(first.readServerMessage(), 90, 1008);
                assertMonsterAttack(second.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertPlayerDie(second.readServerMessage(), first.playerInfo().id(), 90, 1008);
                assertEquals(0L, server.sessions().findByAccount("retaliatea").player().hp());

                for (int expectedHp = 280; expectedHp >= 10; expectedHp -= 10) {
                    second.prepareMonsterAttack(0, 0);
                    second.impactMonster(0);
                    assertMonsterInjure(first.readServerMessage(), 0, 10, expectedHp);
                    assertMonsterInjure(second.readServerMessage(), 0, 10, expectedHp);
                }
                second.prepareMonsterAttack(0, 0);
                second.impactMonster(0);
                assertMonsterDeath(first.readServerMessage(), 0, 10);
                assertMonsterDeath(second.readServerMessage(), 0, 10);
                assertPotentialReward(second.readServerMessage(), 11L);
                assertEquals(1, maps.monsterSnapshots(1, 0).getFirst().status());
                clock.advanceMillis(8_000L);
                assertNoServerMessage(first);
                assertNoServerMessage(second);
                clock.advanceMillis(1L);
                assertMonsterRespawn(first.readServerMessage(), 0, 0, 300L);
                assertMonsterRespawn(second.readServerMessage(), 0, 0, 300L);
                assertNoServerMessage(first);
                assertNoServerMessage(second);

                second.prepareMonsterAttack(0, 0);
                second.impactMonster(0);
                assertMonsterInjure(first.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(second.readServerMessage(), 0, 10, 290);
                clock.advanceMillis(1L);
                assertMonsterAttack(first.readServerMessage(), 0, second.playerInfo().id(), 10L);
                assertMonsterAttack(second.readServerMessage(), 0, second.playerInfo().id(), 10L);
                assertNoServerMessage(first);
                assertEquals(0L, server.sessions().findByAccount("retaliatea").player().hp());
                assertEquals(90L, server.sessions().findByAccount("retaliateb").player().hp());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during retaliation test");
    }

    @Test
    void retaliationPlayerStateSurvivesMoveAndMapChangeOverTcp() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("retaliaterace", "secret1").success());
        MapService maps = new MapService(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources),
                clock,
                new Random(12345L));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources, maps), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            try (LivePlayerClient client = LivePlayerClient.create(
                    server.localPort(), "retaliaterace", "alpha1", 0)) {
                client.finishLoadMap();
                client.move(4464, 936);
                client.requestChangeMap();
                ParsedMapInfo map1 = client.readMapInfo();
                assertEquals(1, map1.mapId());
                assertEquals(90, map1.x());
                assertEquals(1008, map1.y());
                client.finishLoadMap();

                client.prepareMonsterAttack(0, 0);
                client.impactMonster(0);
                assertMonsterInjure(client.readServerMessage(), 0, 10, 290);
                clock.advanceMillis(1L);
                assertMonsterAttack(client.readServerMessage(), 0, client.playerInfo().id(), 10L);
                assertEquals(90L, server.sessions().findByAccount("retaliaterace").player().hp());

                client.move(1260, 640);
                awaitPlayerPosition(server, "retaliaterace", 1260, 640);
                assertEquals(90L, server.sessions().findByAccount("retaliaterace").player().hp());
                assertEquals(1260, server.sessions().findByAccount("retaliaterace").player().x());
                assertEquals(640, server.sessions().findByAccount("retaliaterace").player().y());
                assertNoServerMessage(client);

                client.move(0, 1008);
                client.requestChangeMap();
                ParsedMapInfo map0 = client.readMapInfo();
                assertEquals(0, map0.mapId());
                assertEquals(4374, map0.x());
                assertEquals(936, map0.y());
                assertTrue(map0.monsters().isEmpty());
                assertEquals(90L, server.sessions().findByAccount("retaliaterace").player().hp());
                assertEquals(0, server.sessions().findByAccount("retaliaterace").player().mapId());
                assertNoServerMessage(client);
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during player-state race regression");
    }

    @Test
    void javaClientCreatesFreshPlayerAndParsesLegacyMapZero() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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

    @Test
    void javaClientReceivesLegacyFrameDefinitionsInUnityFieldOrder() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            runFrameRequest(server.localPort(), resources.frames());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during frame integration test");
    }

    @Test
    void javaClientLoadsLegacyLevelResource() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(2_000);
                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                Message version = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.VERSION_SOURCE, version.command());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(-1).toByteArray()));
                Message manifest = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, manifest.command());
                var manifestReader = manifest.reader();
                assertEquals(-1, manifestReader.readByte()); // subtype
                assertEquals(-1, manifestReader.readByte()); // image
                assertEquals(-1, manifestReader.readByte()); // item
                assertEquals(-1, manifestReader.readByte()); // item option
                assertEquals(-1, manifestReader.readByte()); // npc
                assertEquals(2, manifestReader.readByte()); // effect
                assertEquals(1, manifestReader.readByte()); // monster
                assertEquals(-1, manifestReader.readByte()); // medal
                assertEquals(0, manifestReader.readByte()); // level
                assertEquals(1, manifestReader.readByte()); // frame
                assertEquals(-1, manifestReader.readByte()); // mount
                assertEquals(-1, manifestReader.readByte()); // bag
                assertEquals(-1, manifestReader.readByte()); // skill paint
                assertEquals(-1, manifestReader.readByte()); // aura
                assertEquals(0, manifestReader.remaining());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(6).toByteArray()));
                Message levelResponse = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, levelResponse.command());
                var reader = levelResponse.reader();
                assertEquals(6, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(102, reader.readUnsignedShort());
                long previousPower = -1L;
                for (int id = 0; id < 102; id++) {
                    assertEquals(id, reader.readShort());
                    String name = reader.readUtf();
                    long power = reader.readLong();
                    assertTrue(power > previousPower);
                    previousPower = power;
                    if (id == 0 || id == 1) {
                        assertEquals("Tân binh", name);
                        assertEquals(id, power);
                    } else if (id == 2) {
                        assertEquals("Tân binh", name);
                        assertEquals(100L, power);
                    } else if (id == 101) {
                        assertEquals("Thần # cấp 5", name);
                        assertEquals(6_000_000_000_000_000L, power);
                    }
                }
                assertEquals(0, reader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during level resource integration test");
    }

    @Test
    void javaClientLoadsMovementEffectResource() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources),
                null,
                NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP);
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
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(2_000);

                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                assertEquals(MessageName.VERSION_SOURCE,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(-1).toByteArray()));
                Message manifest = codec.readServerResponse(transport.input(), cipher, true);
                var manifestReader = manifest.reader();
                assertEquals(-1, manifestReader.readByte());
                assertEquals(-1, manifestReader.readByte()); // image
                assertEquals(-1, manifestReader.readByte()); // item
                assertEquals(-1, manifestReader.readByte()); // item option
                assertEquals(-1, manifestReader.readByte()); // npc
                assertEquals(2, manifestReader.readByte());  // effect
                assertEquals(1, manifestReader.readByte());  // monster
                assertEquals(-1, manifestReader.readByte()); // medal
                assertEquals(0, manifestReader.readByte());  // level
                assertEquals(1, manifestReader.readByte());  // frame
                assertEquals(-1, manifestReader.readByte()); // mount
                assertEquals(-1, manifestReader.readByte()); // bag
                assertEquals(-1, manifestReader.readByte()); // skill paint
                assertEquals(-1, manifestReader.readByte()); // aura
                assertEquals(0, manifestReader.remaining());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(3).toByteArray()));
                Message response = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, response.command());

                var reader = response.reader();
                assertEquals(3, reader.readByte());
                assertEquals(2, reader.readByte());
                assertEquals(4, reader.readUnsignedShort());
                for (var expected : resources.effects()) {
                    assertEquals(expected.id(), reader.readShort());
                    assertEquals(expected.dx(), reader.readShort());
                    assertEquals(expected.dy(), reader.readShort());
                    assertEquals(expected.delay(), reader.readShort());
                    assertEquals(expected.icons().size(), reader.readUnsignedByte());
                    for (int iconId : expected.icons()) {
                        assertEquals(iconId, reader.readShort());
                    }
                }
                assertEquals(0, reader.readUnsignedShort());
                assertEquals(0, reader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }

        assertNull(serverFailure.get(),
                "network server failed during movement effect integration test");
    }

    @Test
    void javaClientMovesThreeTimesWithoutDisconnecting() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        AuthService auth = new AuthService();
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(auth, resources),
                null,
                NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP);
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
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);

            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(5_000);

                codec.writeClient(
                        transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));

                Message version = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.VERSION_SOURCE, version.command());

                codec.writeClient(
                        transport.output(), cipher, true,
                        new Message(
                                MessageName.REGISTER_USER,
                                new MessageWriter()
                                        .writeUtf("move01")
                                        .writeUtf("secret1")
                                        .toByteArray()));
                assertEquals(
                        MessageName.DIALOG_OK,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                codec.writeClient(
                        transport.output(), cipher, true,
                        new Message(
                                MessageName.LOGIN,
                                new MessageWriter()
                                        .writeUtf("0.9.5")
                                        .writeUtf("move01")
                                        .writeUtf("secret1")
                                        .writeByte(1)
                                        .toByteArray()));
                assertEquals(
                        MessageName.START_CREATE_PLAYER_SCREEN,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                codec.writeClient(
                        transport.output(), cipher, true,
                        new Message(
                                MessageName.CREATE_PLAYER,
                                new MessageWriter()
                                        .writeUtf("mover1")
                                        .writeByte(0)
                                        .toByteArray()));

                Message playerInfo = codec.readServerResponse(transport.input(), cipher, true);
                Message mapInfo = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.PLAYER_INFO, playerInfo.command());
                assertEquals(MessageName.MAP_INFO, mapInfo.command());

                codec.writeClient(
                        transport.output(), cipher, true,
                        new Message(MessageName.FINISH_LOAD_MAP));

                sendMove(codec, transport, cipher, 1260, 648);
                sendMove(codec, transport, cipher, 1284, 620);
                sendMove(codec, transport, cipher, 1312, 648);

                waitForPlayerPosition(server, "move01", 1312, 648);

                Session live = server.sessions().findByAccount("move01");
                assertTrue(live != null);
                assertEquals(SessionState.IN_GAME, live.state());
                assertEquals(1312, live.player().x());
                assertEquals(648, live.player().y());
            }

            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }

        assertNull(serverFailure.get(),
                "network server failed during PLAYER_MOVE integration test");
    }

    @Test
    void javaClientVersionCacheSkipsSecondFrameRequest() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        AtomicInteger frameUpdateCount = new AtomicInteger();
        NetworkEventObserver observer = (session, type) -> {
            if (type == 7) {
                frameUpdateCount.incrementAndGet();
            }
        };
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), observer);
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
            runFrameBootstrap(server.localPort(), true);
            waitForNoSessions(server);
            assertEquals(1, frameUpdateCount.get());

            // Model a restart after the client persisted frame version 1.
            runFrameBootstrap(server.localPort(), false);
            waitForNoSessions(server);
            assertEquals(1, frameUpdateCount.get());
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during frame cache test");
    }

    @Test
    void javaClientReceivesRequestIconResponseWithLegacySpecialFraming(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = new byte[]{1, 2, 3, 4};
        Files.write(iconRoot.resolve("5.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.fromIconRoot(iconRoot)),
                null, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            runIconRequest(server.localPort());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during icon integration test");
    }

    @Test
    void javaClientReceivesLargeIconAboveNormalTwoByteLength(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = patternedBytes(70_000);
        Files.write(iconRoot.resolve("2170.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.fromIconRoot(iconRoot)),
                null, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            runIconRequest(server.localPort(), 2170, iconData);
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during large icon integration test");
    }

    @Test
    void javaClientReceivesObservedLargestIconPayload(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = patternedBytes(127_617);
        Files.write(iconRoot.resolve("2170.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.fromIconRoot(iconRoot)),
                null, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            runIconRequest(server.localPort(), 2170, iconData);
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during observed icon integration test");
    }

    @Test
    void javaClientCompletesLegacyG1BootstrapAndReconnectsWithoutDuplicateMonsterRequest() throws Exception {
        AtomicInteger updateCount = new AtomicInteger();
        AtomicInteger monsterUpdateCount = new AtomicInteger();
        CountDownLatch firstUpdate = new CountDownLatch(1);
        CountDownLatch secondUpdate = new CountDownLatch(1);
        CountDownLatch firstMonsterUpdate = new CountDownLatch(1);
        CountDownLatch secondMonsterUpdate = new CountDownLatch(1);
        NetworkEventObserver observer = (session, type) -> {
            if (type == -1) {
                if (updateCount.incrementAndGet() == 1) {
                    firstUpdate.countDown();
                } else {
                    secondUpdate.countDown();
                }
            } else if (type == 4) {
                if (monsterUpdateCount.incrementAndGet() == 1) {
                    firstMonsterUpdate.countDown();
                } else {
                    secondMonsterUpdate.countDown();
                }
            }
        };
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.fromFrameRoot(
                        Path.of("resources", "json"))),
                null, NetworkConfig.defaults(), observer);
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
            runBootstrap(server.localPort(), true);
            assertTrue(firstUpdate.await(1, TimeUnit.SECONDS));
            assertTrue(firstMonsterUpdate.await(1, TimeUnit.SECONDS));
            waitForNoSessions(server);
            // Model a restart after the client persisted monster version 1.
            runBootstrap(server.localPort(), false);
            assertTrue(secondUpdate.await(1, TimeUnit.SECONDS));
            assertTrue(!secondMonsterUpdate.await(100, TimeUnit.MILLISECONDS));
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during integration test");
    }

    private static void runBootstrap(int port, boolean requestMonster) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            byte[] key = reconstructKey(handshake.payload());
            LegacyCipher cipher = new LegacyCipher(key);
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, manifest.command());
            assertEquals(14, manifest.payload().length);
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, 2, 1, -1, 0, 1, -1, -1, -1, -1
            }, manifest.payload());
            int historicalEmptyMonsterVersion = 0;
            int serverMonsterVersion = Byte.toUnsignedInt(manifest.payload()[6]);
            assertEquals(1, serverMonsterVersion);
            assertTrue(serverMonsterVersion != historicalEmptyMonsterVersion);
            var manifestReader = manifest.reader();
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(2, manifestReader.readByte());
            assertEquals(1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(0, manifestReader.readByte());
            assertEquals(1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(0, manifestReader.remaining());

            if (requestMonster) {
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(4).toByteArray()));
                Message monster = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, monster.command());
                var monsterReader = monster.reader();
                assertEquals(4, monsterReader.readByte());
                assertEquals(1, monsterReader.readByte());
                assertEquals(1, monsterReader.readUnsignedShort());
                assertEquals(0, monsterReader.readShort());
                assertFalse(monsterReader.readBoolean());
                assertEquals(3, monsterReader.readUnsignedByte());
                assertEquals(2198, monsterReader.readShort());
                assertEquals(2199, monsterReader.readShort());
                assertEquals(2200, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(30, monsterReader.readShort());
                assertEquals(3, monsterReader.readUnsignedByte());
                assertEquals(2190, monsterReader.readShort());
                assertEquals(2191, monsterReader.readShort());
                assertEquals(2192, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(30, monsterReader.readShort());
                assertEquals(5, monsterReader.readUnsignedByte());
                assertEquals(2193, monsterReader.readShort());
                assertEquals(2194, monsterReader.readShort());
                assertEquals(2195, monsterReader.readShort());
                assertEquals(2196, monsterReader.readShort());
                assertEquals(2197, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(0, monsterReader.readShort());
                assertEquals(20, monsterReader.readShort());
                assertEquals(1, monsterReader.readUnsignedShort());
                assertEquals(1, monsterReader.readShort());
                assertEquals("Hổ nanh kiếm", monsterReader.readUtf());
                assertEquals(100, monsterReader.readShort());
                assertEquals(1, monsterReader.readByte());
                assertEquals(1, monsterReader.readByte());
                assertEquals(0, monsterReader.readByte());
                assertEquals(5, monsterReader.readUnsignedByte());
                assertEquals(11818, monsterReader.readShort());
                assertEquals(11819, monsterReader.readShort());
                assertEquals(11820, monsterReader.readShort());
                assertEquals(11821, monsterReader.readShort());
                assertEquals(11822, monsterReader.readShort());
                assertEquals(11824, monsterReader.readShort());
                assertEquals(11823, monsterReader.readShort());
                assertEquals(175, monsterReader.readShort());
                assertEquals(95, monsterReader.readShort());
                assertEquals(0, monsterReader.readByte());
                assertEquals(0, monsterReader.readByte());
                assertEquals(0, monsterReader.remaining());
            }
        }
    }

    private static void runFrameRequest(int port, java.util.List<FrameTemplate> expectedFrames) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(4096);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(1_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(7).toByteArray()));
            Message response = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, response.command());
            var reader = response.reader();
            assertEquals(7, reader.readByte());
            assertEquals(1, reader.readByte());
            assertEquals(expectedFrames.size(), reader.readUnsignedShort());
            for (FrameTemplate expected : expectedFrames) {
                assertEquals(expected.id(), reader.readShort());
                assertEquals(expected.hpBar(), reader.readShort());
                assertEquals(expected.chat(), reader.readShort());
                assertEquals(expected.dead().size(), reader.readUnsignedByte());
                for (int iconId : expected.dead()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.stand().size(), reader.readUnsignedByte());
                for (int iconId : expected.stand()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.run().size(), reader.readUnsignedByte());
                for (int iconId : expected.run()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.fly(), reader.readShort());
                assertEquals(expected.jump(), reader.readShort());
                assertEquals(expected.fall(), reader.readShort());
                assertEquals(expected.injure(), reader.readShort());
                assertEquals(expected.action().size(), reader.readUnsignedByte());
                for (var action : expected.action().entrySet()) {
                    assertEquals(action.getKey(), reader.readByte());
                    assertEquals(action.getValue(), reader.readShort());
                }
                assertEquals(expected.dx(), reader.readShort());
                assertEquals(expected.dy(), reader.readShort());
                assertEquals(expected.width(), reader.readShort());
                assertEquals(expected.height(), reader.readShort());
            }
            assertEquals(0, reader.remaining());
        }
    }

    private static void runFrameBootstrap(int port, boolean requestFrame) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(4096);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(1_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, -1, -1, -1, -1
            }, manifest.payload());

            if (requestFrame) {
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(7).toByteArray()));
                Message frame = codec.readServerResponse(transport.input(), cipher, true);
                var reader = frame.reader();
                assertEquals(7, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(9, reader.readUnsignedShort());
            }
        }
    }

    private static void runIconRequest(int port) throws Exception {
        runIconRequest(port, 5, new byte[]{1, 2, 3, 4});
    }

    private static void sendMove(
            LegacyPacketCodec codec,
            LegacyTcpTransport transport,
            LegacyCipher cipher,
            int x,
            int y) throws IOException {
        codec.writeClient(
                transport.output(),
                cipher,
                true,
                new Message(
                        MessageName.PLAYER_MOVE,
                        new MessageWriter()
                                .writeShort(x)
                                .writeShort(y)
                                .toByteArray()));
    }

    private static void waitForPlayerPosition(
            NetworkServer server,
            String accountName,
            int expectedX,
            int expectedY) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Session session = server.sessions().findByAccount(accountName);
            if (session != null
                    && session.player() != null
                    && session.player().x() == expectedX
                    && session.player().y() == expectedY) {
                return;
            }
            Thread.sleep(10);
        }

        Session session = server.sessions().findByAccount(accountName);
        if (session == null) {
            throw new AssertionError("session disappeared before PLAYER_MOVE was observed");
        }
        throw new AssertionError(
                "expected PLAYER_MOVE position "
                        + expectedX + "," + expectedY
                        + " but was "
                        + session.player().x() + "," + session.player().y());
    }

    private static EnterGameResponses runCreatePlayer(int port, String username,
                                                       String name, int gender) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(5_000);
            codec.writeClient(transport.output(), null, false,
                    new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());

            MessageWriter register = new MessageWriter()
                    .writeUtf(username)
                    .writeUtf("secret1");
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REGISTER_USER, register.toByteArray()));
            Message dialog = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.DIALOG_OK, dialog.command());
            assertEquals("Đăng ký thành công", dialog.reader().readUtf());

            MessageWriter login = new MessageWriter()
                    .writeUtf("0.9.5")
                    .writeUtf(username)
                    .writeUtf("secret1")
                    .writeByte(1);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.LOGIN, login.toByteArray()));
            Message createScreen = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.START_CREATE_PLAYER_SCREEN, createScreen.command());
            assertEquals(0, createScreen.payload().length);

            MessageWriter create = new MessageWriter()
                    .writeUtf(name)
                    .writeByte(gender);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.CREATE_PLAYER, create.toByteArray()));
            Message playerMessage = codec.readServerResponse(transport.input(), cipher, true);
            Message mapMessage = codec.readServerResponse(transport.input(), cipher, true);
            return new EnterGameResponses(parsePlayerInfo(playerMessage), parseMapInfo(mapMessage));
        }
    }

    private static EnterGameResponses runLoginExistingPlayer(int port, String username,
                                                              String password) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(5_000);
            codec.writeClient(transport.output(), null, false,
                    new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());

            MessageWriter login = new MessageWriter()
                    .writeUtf("0.9.5")
                    .writeUtf(username)
                    .writeUtf(password)
                    .writeByte(1);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.LOGIN, login.toByteArray()));
            Message playerMessage = codec.readServerResponse(transport.input(), cipher, true);
            Message mapMessage = codec.readServerResponse(transport.input(), cipher, true);
            return new EnterGameResponses(parsePlayerInfo(playerMessage), parseMapInfo(mapMessage));
        }
    }

    private static ParsedPlayerInfo parsePlayerInfo(Message message) throws IOException {
        assertEquals(MessageName.PLAYER_INFO, message.command());
        var reader = message.reader();
        assertEquals(0, reader.readByte());
        int id = reader.readInt();
        String name = reader.readUtf();
        int gender = reader.readByte();
        reader.readLong(); // power
        reader.readLong(); // potential
        reader.readShort(); // level
        reader.readShort(); // point skill
        int head = reader.readShort();
        int body = reader.readShort();
        reader.readShort(); // mount
        reader.readShort(); // bag
        reader.readShort(); // medal
        reader.readShort(); // aura
        int baseDamage = reader.readInt();
        int baseHp = reader.readInt();
        int baseMp = reader.readInt();
        int baseConstitution = reader.readInt();
        reader.readLong(); // potential up damage
        reader.readLong(); // potential up hp
        reader.readLong(); // potential up mp
        reader.readLong(); // potential up constitution
        long maxHp = reader.readLong();
        long maxMp = reader.readLong();
        long hp = reader.readLong();
        long mp = reader.readLong();
        reader.readByte(); // speed
        reader.readByte(); // point pk
        reader.readShort(); // point activity
        reader.readByte(); // barrack count
        reader.readUtf(); // dodge
        reader.readUtf(); // critical
        reader.readUtf(); // reduce damage
        reader.readUtf(); // bloodsucking
        reader.readUtf(); // mana sucking
        reader.readUtf(); // strike back
        reader.readLong(); // damage
        reader.readLong(); // coin
        reader.readLong(); // coin lock
        reader.readInt(); // diamond
        reader.readInt(); // ruby
        reader.readByte(); // spaceship

        int skillCount = reader.readUnsignedByte();
        List<Integer> skillIds = new ArrayList<>(skillCount);
        Map<Integer, List<ParsedPaint>> paintsBySkillId = new LinkedHashMap<>();
        for (int skillIndex = 0; skillIndex < skillCount; skillIndex++) {
            int skillId = reader.readByte();
            skillIds.add(skillId);
            skipUtfList(reader);
            skipUtfList(reader);
            reader.readByte(); // type
            boolean proactive = reader.readBoolean();
            skipShortList(reader);
            skipShortMatrix(reader);
            skipShortMatrix(reader);
            reader.readShort(); // level require
            reader.readByte(); // max level
            reader.readByte(); // max upgrade
            skipIntList(reader);
            skipIntMatrix(reader);
            reader.readByte(); // type mana
            skipIntMatrix(reader);
            int optionCount = reader.readUnsignedByte();
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                reader.readByte();
                reader.readUtf();
                skipShortList(reader);
                skipShortList(reader);
            }
            int level = reader.readByte();
            reader.readByte(); // upgrade
            reader.readInt(); // point
            reader.readByte(); // cooldown reduction
            if (level > 0 && proactive) {
                reader.readLong();
            }
            int paintCount = reader.readUnsignedByte();
            List<ParsedPaint> paints = new ArrayList<>(paintCount);
            for (int paintIndex = 0; paintIndex < paintCount; paintIndex++) {
                paints.add(new ParsedPaint(reader.readUtf(), reader.readShort()));
            }
            paintsBySkillId.put(skillId, List.copyOf(paints));
        }
        int keySkillCount = reader.readUnsignedByte();
        List<Integer> keySkillIds = new ArrayList<>(keySkillCount);
        for (int index = 0; index < keySkillCount; index++) {
            keySkillIds.add((int) reader.readByte());
        }
        int mySkillId = reader.readByte();
        int effectCount = reader.readUnsignedByte();
        for (int index = 0; index < effectCount; index++) {
            reader.readShort();
            reader.readLong();
        }
        assertEquals(0, reader.remaining());
        return new ParsedPlayerInfo(id, name, gender, head, body, baseDamage, baseHp, baseMp,
                baseConstitution, maxHp, maxMp, hp, mp,
                List.copyOf(skillIds), Map.copyOf(paintsBySkillId), List.copyOf(keySkillIds), mySkillId);
    }

    private static ParsedMapInfo parseMapInfo(Message message) throws IOException {
        assertEquals(MessageName.MAP_INFO, message.command());
        return parseMapInfo(message, new HashSet<>());
    }

    private static ParsedMapInfo parseMapInfo(Message message, Set<Integer> cachedMapIds)
            throws IOException {
        assertEquals(MessageName.MAP_INFO, message.command());
        var reader = message.reader();

        int mapId = reader.readShort();
        int iconId = 0;
        String name = null;
        int row = 0;
        int column = 0;
        String data = null;
        List<Integer> imagesBgr = List.of();
        List<List<Integer>> colorsBgr = List.of();
        boolean line = false;
        String dataLine = null;
        if (cachedMapIds.add(mapId)) {
            iconId = reader.readShort();
            name = reader.readUtf();
            row = reader.readShort();
            column = reader.readShort();
            data = reader.readUtf();

            List<Integer> imageValues = new ArrayList<>(3);
            for (int index = 0; index < 3; index++) {
                imageValues.add((int) reader.readShort());
            }
            imagesBgr = List.copyOf(imageValues);

            List<List<Integer>> colorValues = new ArrayList<>(4);
            for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
                List<Integer> color = new ArrayList<>(3);
                for (int columnIndex = 0; columnIndex < 3; columnIndex++) {
                    color.add((int) reader.readShort());
                }
                colorValues.add(List.copyOf(color));
            }
            colorsBgr = List.copyOf(colorValues);

            line = reader.readBoolean();
            dataLine = line ? reader.readUtf() : null;
        }

        int zoneId = reader.readByte();
        int x = reader.readShort();
        int y = reader.readShort();
        int waypointCount = reader.readUnsignedByte();
        List<ParsedWaypoint> waypoints = new ArrayList<>(waypointCount);
        for (int index = 0; index < waypointCount; index++) {
            waypoints.add(new ParsedWaypoint(
                    reader.readShort(), reader.readShort(), reader.readByte(), reader.readUtf()));
        }
        int npcCount = reader.readUnsignedByte();
        int monsterCount = reader.readUnsignedByte();
        List<ParsedMonsterSpawn> monsters = new ArrayList<>(monsterCount);
        for (int index = 0; index < monsterCount; index++) {
            monsters.add(new ParsedMonsterSpawn(
                    reader.readByte(),
                    reader.readShort(),
                    reader.readInt(),
                    reader.readShort(),
                    reader.readByte(),
                    reader.readShort(),
                    reader.readShort(),
                    reader.readLong(),
                    reader.readLong(),
                    reader.readByte()));
        }
        int itemMapCount = reader.readUnsignedShort();
        boolean dragonActive = reader.readBoolean();

        return new ParsedMapInfo(
                mapId, iconId, name, row, column, data,
                List.copyOf(imagesBgr), List.copyOf(colorsBgr), line, dataLine,
                zoneId, x, y, waypoints, npcCount, List.copyOf(monsters), itemMapCount,
                dragonActive, reader.remaining());
    }

    private static void assertFreshPlayer(ParsedPlayerInfo player, String name, int gender,
                                          int head, int body, List<Integer> skillIds) {
        assertTrue(player.id() > 0);
        assertEquals(name, player.name());
        assertEquals(gender, player.gender());
        assertEquals(head, player.head());
        assertEquals(body, player.body());
        assertEquals(10, player.baseDamage());
        assertEquals(5, player.baseHp());
        assertEquals(5, player.baseMp());
        assertEquals(5, player.baseConstitution());
        assertEquals(150, player.maxHp());
        assertEquals(150, player.maxMp());
        assertEquals(100, player.hp());
        assertEquals(100, player.mp());
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

    private static List<ParsedMonsterSpawn> canonicalMap1Monsters() {
        return List.of(
                new ParsedMonsterSpawn(0, 1, 0, 2, 0, 975, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 1, 2, 0, 1348, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 2, 2, 0, 1800, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 3, 2, 0, 2250, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 4, 2, 0, 2600, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 5, 2, 0, 2950, 936, 300L, 300L, 0));
    }

    private static void skipUtfList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readUtf();
        }
    }

    private static void skipShortList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readShort();
        }
    }

    private static void skipShortMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipShortList(reader);
        }
    }

    private static void skipIntList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readInt();
        }
    }

    private static void skipIntMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipIntList(reader);
        }
    }

    private record ParsedPlayerInfo(
            int id,
            String name,
            int gender,
            int head,
            int body,
            int baseDamage,
            int baseHp,
            int baseMp,
            int baseConstitution,
            long maxHp,
            long maxMp,
            long hp,
            long mp,
            List<Integer> skillIds,
            Map<Integer, List<ParsedPaint>> paints,
            List<Integer> keySkillIds,
            int mySkillId
    ) {}

    private record ParsedPaint(String percent, int paintId) {}

    private record EnterGameResponses(
            ParsedPlayerInfo playerInfo,
            ParsedMapInfo mapInfo
    ) {}

    private record ParsedMapInfo(
            int mapId,
            int iconId,
            String name,
            int row,
            int column,
            String data,
            List<Integer> imagesBgr,
            List<List<Integer>> colorsBgr,
            boolean line,
            String dataLine,
            int zoneId,
            int x,
            int y,
            List<ParsedWaypoint> waypoints,
            int npcCount,
            List<ParsedMonsterSpawn> monsters,
            int itemMapCount,
            boolean dragonActive,
            int remaining
    ) {
        int monsterCount() {
            return monsters.size();
        }
    }

    private record ParsedMonsterSpawn(
            int type,
            int templateId,
            int id,
            int level,
            int levelStatus,
            int x,
            int y,
            long maxHp,
            long hp,
            int status
    ) {}

    private record ParsedWaypoint(int x, int y, int type, String name) {}

    private static void runIconRequest(int port, int iconId, byte[] expectedBytes) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(Math.max(1024, expectedBytes.length + 6));
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(2_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REQUEST_ICON, new MessageWriter().writeShort(iconId).toByteArray()));
            Message response = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.REQUEST_ICON, response.command());
            var reader = response.reader();
            assertEquals(iconId, reader.readShort());
            assertEquals(expectedBytes.length, reader.readInt());
            assertArrayEquals(expectedBytes, reader.readBytes(expectedBytes.length));
            assertEquals(0, reader.remaining());
        }
    }

    private static void assertAddPlayer(Message message, int expectedId,
                                        String expectedName, int expectedGender) throws IOException {
        assertEquals(MessageName.ADD_PLAYER, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedName, reader.readUtf());
        assertEquals(expectedGender, reader.readByte());
        int expectedHead = switch (expectedGender) {
            case 0 -> 5;
            case 1 -> 3;
            default -> 4;
        };
        int expectedBody = switch (expectedGender) {
            case 0 -> 6;
            case 1 -> 7;
            default -> 8;
        };
        assertEquals(expectedHead, reader.readShort());
        assertEquals(expectedBody, reader.readShort());
        assertEquals(-1, reader.readShort()); // mount
        assertEquals(-1, reader.readShort()); // bag
        assertEquals(-1, reader.readShort()); // medal
        assertEquals(-1, reader.readShort()); // aura
        assertEquals(1250, reader.readShort());
        assertEquals(648, reader.readShort());
        assertEquals(150, reader.readLong());
        assertEquals(100, reader.readLong());
        assertEquals(0, reader.readByte()); // typePk
        assertEquals(0, reader.readByte()); // typeFlag
        assertEquals(1, reader.readShort());
        assertEquals(0, reader.readByte()); // spaceship
        assertEquals(12, reader.readByte()); // speed
        assertEquals(-1, reader.readInt()); // no clan
        assertEquals(-1, reader.readByte()); // no equipped upgrade
        assertEquals(0, reader.readByte()); // no runtime effects
        assertEquals(0, reader.remaining());
    }

    private static void assertAddPlayerId(Message message, int expectedId) throws IOException {
        assertEquals(MessageName.ADD_PLAYER, message.command());
        assertEquals(expectedId, message.reader().readInt());
    }

    private static void assertMonsterInjure(Message message, int expectedId,
                                             long expectedDamage, long expectedHp) throws IOException {
        assertEquals(MessageName.MONSTER_INJURE, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertEquals(expectedHp, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    private static void assertMonsterDeath(Message message, int expectedId,
                                            long expectedDamage) throws IOException {
        assertEquals(MessageName.MONSTER_START_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    private static void assertPotentialReward(Message message, long expectedPotential)
            throws IOException {
        assertEquals(MessageName.PLAYER_INFO, message.command());
        var reader = message.reader();
        assertEquals(62, reader.readByte());
        assertEquals(expectedPotential, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    private static void assertMonsterRespawn(Message message, int expectedId,
                                              int expectedLevelStatus, long expectedHp)
            throws IOException {
        assertEquals(MessageName.MONSTER_RESPAWN, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedLevelStatus, reader.readByte());
        assertEquals(expectedHp, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    private static void assertMonsterAttack(Message message, int expectedMonsterId,
                                             int expectedPlayerId, long expectedDamage)
            throws IOException {
        assertEquals(MessageName.MONSTER_ATTACK, message.command());
        assertEquals(17, message.payload().length);
        var reader = message.reader();
        assertEquals(expectedMonsterId, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(expectedPlayerId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    private static void assertMeDie(Message message, int expectedX, int expectedY)
            throws IOException {
        assertEquals(MessageName.ME_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedX, reader.readShort());
        assertEquals(expectedY, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    private static void assertPlayerDie(Message message, int expectedPlayerId,
                                         int expectedX, int expectedY) throws IOException {
        assertEquals(MessageName.PLAYER_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedPlayerId, reader.readInt());
        assertEquals(expectedX, reader.readShort());
        assertEquals(expectedY, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    private static void assertNoServerMessage(LivePlayerClient client) throws Exception {
        client.transport.socket().setSoTimeout(250);
        try {
            client.readServerMessage();
            throw new AssertionError("mover received an unexpected server packet");
        } catch (SocketTimeoutException expected) {
            // No movement ACK is part of the inbound PLAYER_MOVE contract.
        } finally {
            client.transport.socket().setSoTimeout(5_000);
        }
    }

    private static final class LivePlayerClient implements AutoCloseable {
        private final LegacyPacketCodec codec;
        private final LegacyTcpTransport transport;
        private final LegacyCipher cipher;
        private final ParsedPlayerInfo playerInfo;
        private final Set<Integer> cachedMapIds;

        private LivePlayerClient(LegacyPacketCodec codec, LegacyTcpTransport transport,
                                 LegacyCipher cipher, ParsedPlayerInfo playerInfo,
                                 Set<Integer> cachedMapIds) {
            this.codec = codec;
            this.transport = transport;
            this.cipher = cipher;
            this.playerInfo = playerInfo;
            this.cachedMapIds = cachedMapIds;
        }

        private static LivePlayerClient create(int port, String username,
                                               String playerName, int gender) throws Exception {
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000);
            try {
                transport.socket().setSoTimeout(5_000);
                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                Message version = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.VERSION_SOURCE, version.command());
                assertEquals("0.9.5", version.reader().readUtf());

                MessageWriter login = new MessageWriter()
                        .writeUtf("0.9.5")
                        .writeUtf(username)
                        .writeUtf("secret1")
                        .writeByte(1);
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.LOGIN, login.toByteArray()));
                assertEquals(MessageName.START_CREATE_PLAYER_SCREEN,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                MessageWriter create = new MessageWriter()
                        .writeUtf(playerName)
                        .writeByte(gender);
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.CREATE_PLAYER, create.toByteArray()));
                ParsedPlayerInfo player = parsePlayerInfo(
                        codec.readServerResponse(transport.input(), cipher, true));
                Set<Integer> cachedMapIds = new HashSet<>();
                parseMapInfo(codec.readServerResponse(transport.input(), cipher, true), cachedMapIds);
                return new LivePlayerClient(codec, transport, cipher, player, cachedMapIds);
            } catch (Throwable failure) {
                try {
                    transport.close();
                } catch (IOException ignored) {
                }
                throw failure;
            }
        }

        private ParsedPlayerInfo playerInfo() {
            return playerInfo;
        }

        private void finishLoadMap() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.FINISH_LOAD_MAP));
        }

        private void returnTownFromDie() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.RETURN_TOWN_FROM_DIE));
        }

        private void prepareMonsterAttack(int skillId, int monsterId) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(
                            MessageName.PLAYER_START_USE_ULTIMATE,
                            new MessageWriter()
                                    .writeByte(skillId)
                                    .writeByte(1)
                                    .writeInt(monsterId)
                                    .toByteArray()));
        }

        private void impactMonster(int monsterId) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(
                            MessageName.USE_SKILL,
                            new MessageWriter()
                                    .writeByte(1)
                                    .writeInt(monsterId)
                                    .toByteArray()));
        }

        private void move(int x, int y) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.PLAYER_MOVE,
                            new MessageWriter().writeShort(x).writeShort(y).toByteArray()));
        }

        private Message readServerMessage() throws IOException {
            return codec.readServerResponse(transport.input(), cipher, true);
        }

        private ParsedMapInfo readMapInfo() throws IOException {
            return parseMapInfo(readServerMessage(), cachedMapIds);
        }

        private void requestChangeMap() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REQUEST_CHANGE_MAP));
        }

        @Override
        public void close() throws IOException {
            transport.close();
        }
    }

    private static byte[] patternedBytes(int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31 + 7);
        }
        return bytes;
    }

    private static byte[] reconstructKey(byte[] payload) {
        int length = Byte.toUnsignedInt(payload[0]);
        byte[] key = new byte[length];
        key[0] = payload[1];
        for (int index = 1; index < length; index++) {
            key[index] = (byte) (Byte.toUnsignedInt(payload[index + 1]) ^ Byte.toUnsignedInt(key[index - 1]));
        }
        return key;
    }

    private static void waitForPort(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.localPort() != 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("server did not bind a port");
    }

    private static void awaitPlayerPosition(NetworkServer server, String account,
                                             int x, int y) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Session session = server.sessions().findByAccount(account);
            if (session != null && session.player() != null
                    && session.player().x() == x && session.player().y() == y) {
                return;
            }
            Thread.sleep(1);
        }
        Session session = server.sessions().findByAccount(account);
        assertTrue(session != null && session.player() != null,
                "player session disappeared while awaiting movement");
        assertEquals(x, session.player().x());
        assertEquals(y, session.player().y());
    }

    private static void waitForNoSessions(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.sessions().onlineCount() == 0) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(server.sessions().onlineCount() == 0, "session leak after disconnect");
    }
}
