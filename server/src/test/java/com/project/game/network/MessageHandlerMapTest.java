package com.project.game.network;
import com.project.game.testsupport.TestPlayerProfiles;

import com.project.game.testsupport.TestServices;

import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.map.MapTemplate;
import com.project.game.map.Zone;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.monster.MonsterFactory;
import com.project.game.account.AuthService;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.game.network.MessageHandlerTestSupport.*;

class MessageHandlerMapTest {

    @Test
    void tracksMapTemplatesPerSession() {
        Session session = newSession(TestServices.authService());

        assertFalse(session.hasSentMapTemplate(0));
        session.markMapTemplateSent(0);
        assertTrue(session.hasSentMapTemplate(0));
        assertFalse(session.hasSentMapTemplate(1));
    }

    @Test
    void changesMapOnlyWhenInsideSupportedWaypoint() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices maps = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        SessionServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = TestPlayerProfiles.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());
        session.markMapTemplateSent(0);

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(start.withLocation(1, 0, 90, 1008), session.player());
        assertEquals(1, session.queuedMessages());
        Message mapInfo = drainMessages(session).getFirst();
        assertEquals(MessageName.MAP_INFO, mapInfo.command());
        var reader = mapInfo.reader();
        assertEquals(1, reader.readShort());
        assertEquals(1, reader.readShort());
        assertEquals("Bờ sông Pu", reader.readUtf());
        assertEquals(20, reader.readShort());
        assertEquals(62, reader.readShort());
        assertEquals(1240, reader.readUtf().length());
        for (int i = 0; i < 3; i++) {
            reader.readShort();
        }
        for (int i = 0; i < 12; i++) {
            reader.readShort();
        }
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readByte());
        assertEquals(90, reader.readShort());
        assertEquals(1008, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readShort());
        assertEquals(1008, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals("Núi Paozu", reader.readUtf());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(6, reader.readUnsignedByte());
        for (int index = 0; index < 6; index++) {
            reader.readByte();
            reader.readShort();
            reader.readInt();
            reader.readShort();
            reader.readByte();
            reader.readShort();
            reader.readShort();
            reader.readLong();
            reader.readLong();
            reader.readByte();
        }
        assertEquals(0, reader.readUnsignedShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
        assertEquals(0, maps.memberCount(1, 0));

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        assertEquals(1, maps.memberCount(1, 0));
    }

    @Test
    void requestChangeMapOutsideWaypointIsNoOp() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices maps = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        SessionServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = TestPlayerProfiles.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 1250, 648);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(start, session.player());
        assertEquals(0, session.queuedMessages());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void requestChangeMapPreservesAuthoritativeHpChangedBeforeZoneTransition() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices maps = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(resources));
        SessionServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = TestPlayerProfiles.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());
        maps.finishLoad(session);
        drainMessages(session);
        Zone sourceZone = zoneFor(maps, 0, 0);

        Thread transition;
        synchronized (sourceZone) {
            transition = Thread.ofVirtual().start(() ->
                    handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP)));
            awaitBlocked(transition);
            session.bindPlayer(session.player().withHp(90));
        }
        transition.join();

        assertEquals(90L, session.player().hp());
        assertEquals(1, session.player().mapId());
        assertEquals(90, session.player().x());
        assertEquals(1008, session.player().y());
    }

    @Test
    void requestChangeMapRejectsNonEmptyPayload() {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        Session session = inGameSession(TestServices.serverServices(TestServices.authService(), resources),
                TestPlayerProfiles.initial(1L, 7, "alpha1", 0));

        newHandler(session, resources).onMessage(new Message(
                MessageName.REQUEST_CHANGE_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void mapInfoRevisitUsesCachedTemplateLayout() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        Session session = inGameSession(TestServices.serverServices(TestServices.authService(), resources),
                TestPlayerProfiles.initial(1L, 7, "alpha1", 0)
                        .withLocation(0, 0, 4464, 936));
        MessageHandler handler = newHandler(session, resources);
        session.markMapTemplateSent(0);

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        drainMessages(session);
        session.bindPlayer(session.player().withLocation(1, 0, 20, 1008));
        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        Message mapInfo = drainMessages(session).getFirst();

        var reader = mapInfo.reader();
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(4374, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(4464, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals("Bờ sông Pu", reader.readUtf());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readUnsignedShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void finishLoadRegistersPresenceAndMovementDoesNotAckMover() throws Exception {
        AuthService auth = TestServices.authService();
        GameplayServices maps = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(GameResources.unavailable()));
        SessionServices services = TestServices.serverServices(auth, GameResources.unavailable(), maps);
        Session first = inGameSession(services, TestPlayerProfiles.initial(1L, 1, "alpha1", 0));
        Session second = inGameSession(services, TestPlayerProfiles.initial(2L, 2, "beta22", 0));
        MessageHandler firstHandler = newHandler(first, services, ClientConfig.defaults());
        MessageHandler secondHandler = newHandler(second, services, ClientConfig.defaults());

        firstHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        secondHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        assertEquals(1, first.queuedMessages());
        assertEquals(1, second.queuedMessages());
        drainMessages(first);
        drainMessages(second);

        secondHandler.onMessage(moveMessage(1260, 640));
        assertEquals(1, first.queuedMessages());
        assertEquals(0, second.queuedMessages());
        Message movement = drainMessages(first).get(0);
        var reader = movement.reader();
        assertEquals(MessageName.PLAYER_MOVE, movement.command());
        assertEquals(2, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void playerMoveIsAcceptedInGameAndUpdatesSessionPosition() {
        AuthService auth = TestServices.authService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1284, 620));
        handler.onMessage(moveMessage(1312, 648));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(1312, session.player().x());
        assertEquals(648, session.player().y());
        assertEquals(0, session.queuedMessages());
    }

    @Test
    void finishLoadMapIsAcceptedInGameWithoutConsumingViolationBudget() {
        Session session = newSession(TestServices.authService());
        session.bindPlayer(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        MessageHandler handler = newHandler(session, TestServices.authService());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
    }

    @Test
    void finishLoadMapRejectsTrailingBytes() {
        GameplayServices maps = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(GameResources.unavailable()));
        SessionServices services = TestServices.serverServices(TestServices.authService(), GameResources.unavailable(), maps);
        Session session = inGameSession(services, TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void finishLoadMapClosesSessionWhenZoneIsFull() throws Exception {
        Map<Integer, MapTemplate> mapCatalog = policyMaps("ONLINE", "ONLINE", 1, 1);
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), mapCatalog, 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices gameplay = new GameplayServices(mapCatalog, resources);
        SessionServices services = TestServices.serverServices(
                TestServices.authService(), resources, gameplay);
        Session first = inGameSession(services,
                TestPlayerProfiles.initial(1L, 1, "alpha1", 0));
        Session second = inGameSession(services,
                TestPlayerProfiles.initial(2L, 2, "beta22", 0));

        newHandler(first, services, ClientConfig.defaults())
                .onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        drainMessages(first);
        newHandler(second, services, ClientConfig.defaults())
                .onMessage(new Message(MessageName.FINISH_LOAD_MAP));

        assertEquals(SessionState.IN_GAME, first.state());
        assertEquals(SessionState.CLOSED, second.state());
        assertEquals(1, gameplay.memberCount(0, 0));
    }

    @Test
    void finishLoadMapClosesConflictingSessionWithoutReplacingOriginalMember() throws Exception {
        AuthService auth = TestServices.authService();
        GameplayServices gameplay = new GameplayServices(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterFactory(GameResources.unavailable()));
        SessionServices services = TestServices.serverServices(
                auth, GameResources.unavailable(), gameplay);
        Session first = inGameSession(services,
                TestPlayerProfiles.initial(7L, 7, "alpha1", 0));
        Session conflicting = inGameSession(services,
                TestPlayerProfiles.initial(8L, 7, "alpha2", 0));
        MessageHandler firstHandler = newHandler(first, services, ClientConfig.defaults());
        MessageHandler conflictingHandler = newHandler(
                conflicting, services, ClientConfig.defaults());

        firstHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));
        drainMessages(first);
        conflictingHandler.onMessage(new Message(MessageName.FINISH_LOAD_MAP));

        assertEquals(SessionState.IN_GAME, first.state());
        assertEquals(SessionState.CLOSED, conflicting.state());
        assertEquals(1, gameplay.memberCount(0, 0));
        assertTrue(gameplay.findZone(0, 0).contains(first));
        assertFalse(gameplay.findZone(0, 0).contains(conflicting));
        assertEquals(0, first.queuedMessages());
    }

    @Test
    void requestChangeMapDoesNotEmitMapInfoWhenDestinationIsOffline() throws Exception {
        Map<Integer, MapTemplate> mapCatalog = policyMaps("ONLINE", "OFFLINE", 1, 1);
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), mapCatalog, 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices gameplay = new GameplayServices(mapCatalog, resources);
        SessionServices services = TestServices.serverServices(
                TestServices.authService(), resources, gameplay);
        PlayerProfile start = TestPlayerProfiles.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(start, session.player());
        assertEquals(0, session.queuedMessages());
        assertEquals(0, gameplay.memberCount(0, 0));
    }

    private static Map<Integer, MapTemplate> policyMaps(
            String map0Type, String map1Type, int map0MaxPlayer, int map1MaxPlayer) {
        Map<Integer, MapTemplate> canonical = MapTestSupport.canonicalMaps();
        MapTemplate map0 = withPolicy(canonical.get(0), map0Type, 1, 3, map0MaxPlayer);
        MapTemplate map1 = withPolicy(canonical.get(1), map1Type, 1, 3, map1MaxPlayer);
        return Map.of(map0.id(), map0, map1.id(), map1);
    }

    private static MapTemplate withPolicy(
            MapTemplate map, String type, int minZone, int maxZone, int maxPlayer) {
        return new MapTemplate(
                map.id(), map.name(), type, map.planet(), minZone, maxZone, maxPlayer,
                map.dataId(), map.data(), map.waypoints());
    }

    @Test
    void playerMoveRejectsTruncatedPayload() {
        AuthService auth = TestServices.authService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        byte[] truncated = new MessageWriter()
                .writeShort(1260)
                .writeByte(1)
                .toByteArray();

        handler.onMessage(new Message(MessageName.PLAYER_MOVE, truncated));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveRejectsTrailingPayloadBytes() {
        AuthService auth = TestServices.authService();
        Session session = inGameSessionWithPlayer(auth);
        MessageHandler handler = newHandler(session, auth);

        byte[] trailing = new MessageWriter()
                .writeShort(1260)
                .writeShort(648)
                .writeByte(1)
                .toByteArray();

        handler.onMessage(new Message(MessageName.PLAYER_MOVE, trailing));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveRemainsRejectedBeforeInGame() {
        AuthService auth = TestServices.authService();
        Session session = newSession(auth);
        session.bindPlayer(TestPlayerProfiles.initial(1L, 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1260, 648));
        handler.onMessage(moveMessage(1260, 648));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void playerMoveWithoutBoundPlayerFailsClosed() {
        AuthService auth = TestServices.authService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);

        newHandler(session, auth).onMessage(moveMessage(1260, 648));

        assertEquals(SessionState.CLOSED, session.state());
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.BLOCKED, thread.getState(),
                "map transition did not block on source zone");
    }
}
