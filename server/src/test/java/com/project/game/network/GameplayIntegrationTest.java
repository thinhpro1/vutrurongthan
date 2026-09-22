package com.project.game.network;

import com.project.game.testsupport.TestServices;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.monster.MonsterFactory;
import com.project.game.account.AuthService;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.IntegrationTestSupport.*;

class GameplayIntegrationTest {

    @Test
    void javaClientDiesToMonsterAndReturnsTown() throws Exception {
        String victimAccount = "deathrevivea";
        String observerAccount = "deathreviveb";
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        MutableClock clock = new MutableClock(1_000_000L);
        BlockingLifecycleRandom random = new BlockingLifecycleRandom();
        assertTrue(auth.register(victimAccount, "secret1", "127.0.0.1").success());
        assertTrue(auth.register(observerAccount, "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources),
                clock,
                random);
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
            try (LivePlayerClient victim = LivePlayerClient.create(
                    server.localPort(), victimAccount, "victim1", 0);
                 LivePlayerClient observer = LivePlayerClient.create(
                         server.localPort(), observerAccount, "observer1", 1)) {
                victim.finishLoadMap();
                observer.finishLoadMap();
                assertAddPlayer(observer.readServerMessage(), victim.playerInfo().id(), "victim1", 0);
                assertAddPlayer(victim.readServerMessage(), observer.playerInfo().id(), "observer1", 1);

                victim.move(4464, 936);
                assertEquals(MessageName.PLAYER_MOVE, observer.readServerMessage().command());
                victim.requestChangeMap();
                ParsedMapInfo victimMap1 = victim.readMapInfo();
                assertEquals(1, victimMap1.mapId());
                victim.finishLoadMap();
                assertEquals(MessageName.REMOVE_PLAYER, observer.readServerMessage().command());

                observer.move(4464, 936);
                observer.requestChangeMap();
                ParsedMapInfo observerMap1 = observer.readMapInfo();
                assertEquals(1, observerMap1.mapId());
                observer.finishLoadMap();
                assertAddPlayerId(victim.readServerMessage(), observer.playerInfo().id());
                assertAddPlayerId(observer.readServerMessage(), victim.playerInfo().id());

                victim.prepareMonsterAttack(0, 0);
                victim.impactMonster(0);
                assertMonsterInjure(victim.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(observer.readServerMessage(), 0, 10, 290);

                assertTrue(random.entered.await(5, TimeUnit.SECONDS),
                        "monster lifecycle scheduler did not reach target selection");
                Session dead = server.sessions().findByAccount(victimAccount);
                assertTrue(dead != null);
                long expectedMaxHp = dead.player().currentStats().maxHp();
                long expectedMaxMp = dead.player().currentStats().maxMp();
        dead.bindPlayer(dead.player().withHp(10));
                random.release.countDown();

                assertMonsterAttack(victim.readServerMessage(), 0, victim.playerInfo().id(), 10L);
                assertMeDie(victim.readServerMessage(), 90, 1008);
                assertMonsterAttack(observer.readServerMessage(), 0, victim.playerInfo().id(), 10L);
                assertPlayerDie(observer.readServerMessage(), victim.playerInfo().id(), 90, 1008);
                assertEquals(0L, server.sessions().findByAccount(victimAccount).player().hp());

                Session beforePaidRevive = server.sessions().findByAccount(victimAccount);
                assertTrue(beforePaidRevive != null);
                int deadMapId = beforePaidRevive.player().mapId();
                int deadZoneId = beforePaidRevive.player().zoneId();
                int deadX = beforePaidRevive.player().x();
                int deadY = beforePaidRevive.player().y();
                int rubyBefore = beforePaidRevive.player().ruby();
                int diamondBefore = beforePaidRevive.player().diamond();

                victim.wakeUpFromDieRequest();
                victim.wakeUpFromDieRequest();
                victim.wakeUpFromDieRequest();
                assertNoServerMessage(victim);

                Session afterPaidRevive = server.sessions().findByAccount(victimAccount);
                assertTrue(afterPaidRevive != null);
                assertEquals(SessionState.IN_GAME, afterPaidRevive.state());
                assertEquals(0L, afterPaidRevive.player().hp());
                assertEquals(deadMapId, afterPaidRevive.player().mapId());
                assertEquals(deadZoneId, afterPaidRevive.player().zoneId());
                assertEquals(deadX, afterPaidRevive.player().x());
                assertEquals(deadY, afterPaidRevive.player().y());
                assertEquals(rubyBefore, afterPaidRevive.player().ruby());
                assertEquals(diamondBefore, afterPaidRevive.player().diamond());

                clock.advanceMillis(2_000L);
                assertNoServerMessage(victim);
                assertNoServerMessage(observer);

                victim.returnTownFromDie();
                ParsedMapInfo town = victim.readMapInfo();
                assertEquals(0, town.mapId());
                assertEquals(0, town.zoneId());
                assertEquals(1250, town.x());
                assertEquals(648, town.y());

                Message wake = victim.readServerMessage();
                assertEquals(MessageName.WAKE_UP_FROM_DIE, wake.command());
                var wakeReader = wake.reader();
                assertEquals(victim.playerInfo().id(), wakeReader.readInt());
                assertEquals(1250, wakeReader.readShort());
                assertEquals(648, wakeReader.readShort());
                assertEquals(expectedMaxHp, wakeReader.readLong());
                assertEquals(expectedMaxMp, wakeReader.readLong());
                assertEquals(0, wakeReader.remaining());

                Message removed = observer.readServerMessage();
                assertEquals(MessageName.REMOVE_PLAYER, removed.command());
                var removeReader = removed.reader();
                assertEquals(victim.playerInfo().id(), removeReader.readInt());
                assertEquals(0, removeReader.remaining());

                victim.finishLoadMap();
                Session revived = server.sessions().findByAccount(victimAccount);
                assertTrue(revived != null);
                assertEquals(0, revived.player().mapId());
                assertEquals(0, revived.player().zoneId());
                assertEquals(1250, revived.player().x());
                assertEquals(648, revived.player().y());
                assertEquals(revived.player().currentStats().maxHp(), revived.player().hp());
                assertEquals(revived.player().currentStats().maxMp(), revived.player().mp());
                awaitMemberCount(maps, 0, 0, 1);
                assertEquals(1, maps.memberCount(0, 0));

                victim.move(1260, 648);
                awaitPlayerPosition(server, victimAccount, 1260, 648);
            }
            waitForNoSessions(server);
        } finally {
            random.release.countDown();
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during death/revive lifecycle test");
    }

    @Test
    void livingPlayerReturnTownRequestIsIgnored() throws Exception {
        String accountName = "livingreturn";
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        assertTrue(auth.register(accountName, "secret1", "127.0.0.1").success());
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources), null,
                ClientConfig.defaults());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        assertTrue(auth.register("mapround1", "secret1", "127.0.0.1").success());
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources), null,
                ClientConfig.defaults());
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
                assertMap1MonsterShape(map1.monsters());
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
                assertMap1MonsterShape(secondMap1.monsters());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        assertTrue(auth.register("mapzonea", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("mapzoneb", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
                assertMap1MonsterShape(firstMap1.monsters());
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
                assertMap1MonsterShape(secondMap1.monsters());
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
    void javaClientsObserveAuthoritativeMonsterMovementAndChase() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("chasetcp1", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("chasetcp2", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("chasetcp3", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources),
                clock,
                new Random(12345L));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
            try (LivePlayerClient attacker = LivePlayerClient.create(
                    server.localPort(), "chasetcp1", "chaser1", 0);
                 LivePlayerClient observer = LivePlayerClient.create(
                         server.localPort(), "chasetcp2", "chaser2", 1)) {
                attacker.finishLoadMap();
                observer.finishLoadMap();
                assertAddPlayer(observer.readServerMessage(), attacker.playerInfo().id(), "chaser1", 0);
                assertAddPlayer(attacker.readServerMessage(), observer.playerInfo().id(), "chaser2", 1);

                attacker.move(4464, 936);
                attacker.requestChangeMap();
                assertEquals(1, attacker.readMapInfo().mapId());
                attacker.finishLoadMap();
                assertEquals(MessageName.PLAYER_MOVE, observer.readServerMessage().command());
                assertEquals(MessageName.REMOVE_PLAYER, observer.readServerMessage().command());

                observer.move(4464, 936);
                observer.requestChangeMap();
                assertEquals(1, observer.readMapInfo().mapId());
                observer.finishLoadMap();
                assertAddPlayerId(attacker.readServerMessage(), observer.playerInfo().id());
                assertAddPlayerId(observer.readServerMessage(), attacker.playerInfo().id());

                attacker.move(2_100, 936);
                assertEquals(MessageName.PLAYER_MOVE, observer.readServerMessage().command());

                attacker.prepareMonsterAttack(0, 0);
                attacker.impactMonster(0);
                assertMonsterInjure(attacker.readServerMessage(), 0, 10, 290);
                assertMonsterInjure(observer.readServerMessage(), 0, 10, 290);

                MonsterMoveView attackerMove = readMonsterMove(attacker, 0);
                MonsterMoveView observerMove = readMonsterMove(observer, 0);
                assertEquals(attackerMove, observerMove);
                assertEquals(0, attackerMove.monsterId());
                assertEquals(1, attackerMove.dir());
                assertTrue(attackerMove.x() > 975);
                assertTrue(maps.monsterSnapshots(1, 0).getFirst().x() >= attackerMove.x());

                MonsterMoveView stopped = attackerMove;
                while (stopped.x() < 1_203) {
                    stopped = readMonsterMove(attacker, 0);
                }
                assertEquals(1_203, stopped.x());
                int authoritativeX = maps.monsterSnapshots(1, 0).getFirst().x();
                assertEquals(stopped.x(), authoritativeX);

                try (LivePlayerClient late = LivePlayerClient.create(
                        server.localPort(), "chasetcp3", "chaser3", 0)) {
                    late.move(4464, 936);
                    late.requestChangeMap();
                    ParsedMapInfo lateMap = late.readMapInfo();
                    assertEquals(1, lateMap.mapId());
                    assertEquals(stopped.x(), lateMap.monsters().getFirst().x());
                    assertEquals(stopped.y(), lateMap.monsters().getFirst().y());
                    late.finishLoadMap();
                    assertAddPlayerId(attacker.readServerMessage(), late.playerInfo().id());
                    assertAddPlayerId(observer.readServerMessage(), late.playerInfo().id());
                    assertAddPlayerId(late.readServerMessage(), attacker.playerInfo().id());
                    assertAddPlayerId(late.readServerMessage(), observer.playerInfo().id());
                }
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during TCP movement/chase test");
    }

    @Test
    void twoClientsSeeSameZonePresenceMovementAndDisconnect() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        assertTrue(auth.register("zonea1", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("zoneb1", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("combatza", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("combatzb", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources),
                clock);
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
                assertMap1MonsterShape(firstMap1.monsters());
                first.finishLoadMap();
                assertEquals(MessageName.PLAYER_MOVE, second.readServerMessage().command());
                assertEquals(MessageName.REMOVE_PLAYER, second.readServerMessage().command());

                second.move(4464, 936);
                second.requestChangeMap();
                ParsedMapInfo secondMap1 = second.readMapInfo();
                assertMap1MonsterShape(secondMap1.monsters());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        assertTrue(auth.register(accountName, "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
                assertEquals(200L, live.player().hp());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("retaliatea", "secret1", "127.0.0.1").success());
        assertTrue(auth.register("retaliateb", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources),
                clock,
                new Random(12345L));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 4, 262_144, 16, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
                assertEquals(190L, server.sessions().findByAccount("retaliatea").player().hp());

                clock.advanceMillis(1_600L);
                assertNoServerMessage(first);
                assertNoServerMessage(second);
                clock.advanceMillis(1L);
                assertMonsterAttack(first.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertMonsterAttack(second.readServerMessage(), 0, first.playerInfo().id(), 10L);
                assertEquals(180L, server.sessions().findByAccount("retaliatea").player().hp());

                for (int expectedHp = 170; expectedHp >= 10; expectedHp -= 10) {
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
                assertEquals(190L, server.sessions().findByAccount("retaliateb").player().hp());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        MutableClock clock = new MutableClock(1_000_000L);
        assertTrue(auth.register("retaliaterace", "secret1", "127.0.0.1").success());
        GameplayServices maps = new GameplayServices(
                new com.project.game.network.packet.PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources),
                clock,
                new Random(12345L));
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources, maps), null,
                ClientConfig.defaults());
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
                assertEquals(190L, server.sessions().findByAccount("retaliaterace").player().hp());

                client.move(1260, 640);
                awaitPlayerPosition(server, "retaliaterace", 1260, 640);
                assertEquals(190L, server.sessions().findByAccount("retaliaterace").player().hp());
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
                assertEquals(190L, server.sessions().findByAccount("retaliaterace").player().hp());
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
    void javaClientMovesThreeTimesWithoutDisconnecting() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2);
        AuthService auth = TestServices.authService();
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(auth, resources),
                null,
                ClientConfig.defaults());
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

    private static List<ParsedMonsterSpawn> canonicalMap1Monsters() {
        return List.of(
                new ParsedMonsterSpawn(0, 1, 0, 2, 0, 975, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 1, 2, 0, 1348, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 2, 2, 0, 1800, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 3, 2, 0, 2250, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 4, 2, 0, 2600, 936, 300L, 300L, 0),
                new ParsedMonsterSpawn(0, 1, 5, 2, 0, 2950, 936, 300L, 300L, 0));
    }

    private static void assertMap1MonsterShape(List<ParsedMonsterSpawn> actual) {
        List<ParsedMonsterSpawn> expected = canonicalMap1Monsters();
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            ParsedMonsterSpawn expectedMonster = expected.get(index);
            ParsedMonsterSpawn actualMonster = actual.get(index);
            assertEquals(expectedMonster.type(), actualMonster.type());
            assertEquals(expectedMonster.templateId(), actualMonster.templateId());
            assertEquals(expectedMonster.id(), actualMonster.id());
            assertEquals(expectedMonster.level(), actualMonster.level());
            assertEquals(expectedMonster.levelStatus(), actualMonster.levelStatus());
            assertTrue(Math.abs(actualMonster.x() - expectedMonster.x()) <= 100);
            assertEquals(expectedMonster.y(), actualMonster.y());
            assertEquals(expectedMonster.maxHp(), actualMonster.maxHp());
            assertEquals(expectedMonster.hp(), actualMonster.hp());
            assertEquals(expectedMonster.status(), actualMonster.status());
        }
    }

    private static final class BlockingLifecycleRandom implements RandomGenerator {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public int nextInt(int bound) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("monster lifecycle selection was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return 0;
        }

        @Override
        public long nextLong() {
            return 0L;
        }
    }
}
