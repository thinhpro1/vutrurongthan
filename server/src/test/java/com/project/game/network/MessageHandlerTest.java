package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.testsupport.TestAccountRepository;
import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.AccountRepository;
import com.project.game.persistence.account.AccountRepositoryException;

import com.project.game.map.MapService;
import com.project.game.map.Zone;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.service.AuthService;
import com.project.game.service.IconFingerprint;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHandlerTest {
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
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        ServerServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());
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
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        ServerServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 1250, 648);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());

        handler.onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));

        assertEquals(SessionState.IN_GAME, session.state());
        assertEquals(start, session.player());
        assertEquals(0, session.queuedMessages());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void requestChangeMapPreservesAuthoritativeHpChangedBeforeZoneTransition() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        ServerServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        PlayerProfile start = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 0, 4464, 936);
        Session session = inGameSession(services, start);
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());
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
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        Session session = inGameSession(TestServices.serverServices(TestServices.authService(), resources),
                PlayerProfile.initial(1L, 7, "alpha1", 0));

        newHandler(session, resources).onMessage(new Message(
                MessageName.REQUEST_CHANGE_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void mapInfoRevisitUsesCachedTemplateLayout() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        Session session = inGameSession(TestServices.serverServices(TestServices.authService(), resources),
                PlayerProfile.initial(1L, 7, "alpha1", 0)
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
        MapService maps = new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(ResourceService.unavailable()));
        ServerServices services = TestServices.serverServices(auth, ResourceService.unavailable(), maps);
        Session first = inGameSession(services, PlayerProfile.initial(1L, 1, "alpha1", 0));
        Session second = inGameSession(services, PlayerProfile.initial(2L, 2, "beta22", 0));
        MessageHandler firstHandler = newHandler(first, services, NetworkConfig.defaults());
        MessageHandler secondHandler = newHandler(second, services, NetworkConfig.defaults());

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
        MapService maps = new MapService(
                new PlayerPacketWriter(),
                new MonsterPacketWriter(),
                new MonsterRuntimeFactory(ResourceService.unavailable()));
        ServerServices services = TestServices.serverServices(TestServices.authService(), ResourceService.unavailable(), maps);
        Session session = inGameSession(services, PlayerProfile.initial(1L, 7, "alpha1", 0));
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());

        handler.onMessage(new Message(MessageName.FINISH_LOAD_MAP, new byte[]{1}));

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, maps.memberCount(0, 0));
    }

    @Test
    void impactWithoutPrepareDoesNotDamageMonster() {
        CombatContext context = combatContext();

        context.handler().onMessage(monsterImpact(0));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void prepareThenImpactAppliesExactlyOneHit() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 0));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(290L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void replayImpactAfterPendingConsumedDoesNotDamageAgain() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 0));
        context.handler().onMessage(monsterImpact(0));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(290L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void mismatchedImpactConsumesPendingAndDoesNotRetainIt() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 0));
        context.handler().onMessage(monsterImpact(1));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void oneBytePrepareClearsPending() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 0));
        context.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).toByteArray()));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void playerTargetPrepareIsValidNoOp() {
        CombatContext context = combatContext();

        context.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).writeByte(0).writeInt(99).toByteArray()));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void noTargetAndPlayerImpactAreValidNoOps() {
        CombatContext context = combatContext();

        context.handler().onMessage(new Message(
                MessageName.USE_SKILL,
                new MessageWriter().writeByte(-1).toByteArray()));
        context.handler().onMessage(new Message(
                MessageName.USE_SKILL,
                new MessageWriter().writeByte(0).writeInt(99).toByteArray()));

        assertEquals(SessionState.IN_GAME, context.session().state());
        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void malformedCombatTargetTypesAndTrailingBytesCloseSession() {
        CombatContext prepareType = combatContext();
        prepareType.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).writeByte(2).writeInt(0).toByteArray()));
        assertEquals(SessionState.CLOSED, prepareType.session().state());

        CombatContext impactType = combatContext();
        impactType.handler().onMessage(new Message(
                MessageName.USE_SKILL,
                new MessageWriter().writeByte(2).toByteArray()));
        assertEquals(SessionState.CLOSED, impactType.session().state());

        CombatContext prepareTrailing = combatContext();
        prepareTrailing.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).writeByte(1).writeInt(0).writeByte(1).toByteArray()));
        assertEquals(SessionState.CLOSED, prepareTrailing.session().state());

        CombatContext impactTrailing = combatContext();
        impactTrailing.handler().onMessage(new Message(
                MessageName.USE_SKILL,
                new MessageWriter().writeByte(-1).writeByte(1).toByteArray()));
        assertEquals(SessionState.CLOSED, impactTrailing.session().state());
    }

    @Test
    void preFinishMapInfoZoneCannotBeTargeted() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        ServerServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        Session session = inGameSession(services,
                PlayerProfile.initial(1L, 7, "alpha1", 0).withLocation(1, 0, 90, 1008));
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());
        maps.monsterSnapshots(1, 0);

        handler.onMessage(prepareMonster(7, 0));
        handler.onMessage(monsterImpact(0));

        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void mapChangeClearsPendingMonsterAttack() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 0));
        context.session().bindPlayer(context.session().player().withLocation(1, 0, 0, 1008));
        context.handler().onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        context.handler().onMessage(monsterImpact(0));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(0, context.session().player().mapId());
    }

    @Test
    void closesWhenLoginOmitsRequiredLoginVersion() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01").writeUtf("secret1");

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void registerUsesServerDerivedRemoteAddress() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "192.0.2.44");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(new Message(
                MessageName.REGISTER_USER,
                new MessageWriter().writeUtf("user01").writeUtf("secret1").toByteArray()));

        assertEquals("192.0.2.44", repository.requireAccount("user01").ipAddress());
    }

    @Test
    void validLoginBindsThenUpdatesMetadataThenAuthenticates() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(session, manager.findByAccount("user01"));
        assertEquals(1, repository.metadataUpdateCount());
        assertEquals("198.51.100.1", repository.requireAccount("user01").ipAddress());
        assertEquals(MessageName.START_CREATE_PLAYER_SCREEN, drainMessages(session).getFirst().command());
    }

    @Test
    void duplicateOnlineLoginDoesNotUpdateSuccessfulLoginMetadata() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session first = newSession(auth, 1024, manager, "198.51.100.1");
        Session second = newSession(auth, 1024, manager, "198.51.100.2");
        first.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        second.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(first, auth).onMessage(loginMessage("user01", "secret1"));
        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, first.state());
        assertEquals(SessionState.HANDSHAKE_DONE, second.state());
        assertEquals(first, manager.findByAccount("user01"));
        assertEquals(1, repository.metadataUpdateCount());
        assertEquals("198.51.100.1", repository.requireAccount("user01").ipAddress());
        Message dialog = drainMessages(second).getFirst();
        assertEquals(MessageName.DIALOG_OK, dialog.command());
        assertEquals("Tài khoản đang đăng nhập ở thiết bị khác", dialog.reader().readUtf());
    }

    @Test
    void metadataFailureRollsBackAccountBindingAndStaysUnauthenticated() throws Exception {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        repository.failUpdate(true);
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(null, session.player());
        Message dialog = drainMessages(session).getFirst();
        assertEquals(MessageName.DIALOG_OK, dialog.command());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", dialog.reader().readUtf());
    }

    @Test
    void closeFinishesWhileSuccessfulLoginMetadataUpdateIsBlocked() throws Exception {
        BlockingMetadataRepository repository = new BlockingMetadataRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session session = newSession(auth, 1024, manager, "198.51.100.1");
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        Thread login = Thread.ofVirtual().start(() -> {
            try {
                newHandler(session, auth).onMessage(loginMessage("user01", "secret1"));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        assertTrue(repository.updateEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeInvoked = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            closeInvoked.countDown();
            session.close();
            closeFinished.countDown();
        });
        assertTrue(closeInvoked.await(1, TimeUnit.SECONDS));
        try {
            assertTrue(closeFinished.await(1, TimeUnit.SECONDS),
                    "session close waited for the metadata repository call");
            assertEquals(SessionState.CLOSED, session.state());
            assertSame(session, manager.findByAccount("user01"));
        } finally {
            repository.allowUpdate.countDown();
        }
        login.join(1_000);
        close.join(1_000);

        assertFalse(login.isAlive());
        assertFalse(close.isAlive());
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(null, manager.findByAccount("user01"));
        assertEquals(1, repository.delegate.metadataUpdateCount());
    }

    @Test
    void reconnectWaitsForClosedSessionLoginAdmissionToFinish() throws Exception {
        BlockingMetadataRepository repository = new BlockingMetadataRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        SessionManager manager = new SessionManager();
        Session first = newSession(auth, 1024, manager, "198.51.100.1");
        Session second = newSession(auth, 1024, manager, "198.51.100.2");
        first.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        second.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        Thread firstLogin = Thread.ofVirtual().start(() -> {
            try {
                newHandler(first, auth).onMessage(loginMessage("user01", "secret1"));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        assertTrue(repository.updateEntered.await(1, TimeUnit.SECONDS));

        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread close = Thread.ofVirtual().start(() -> {
            first.close();
            closeFinished.countDown();
        });
        assertTrue(closeFinished.await(1, TimeUnit.SECONDS));
        assertEquals(SessionState.CLOSED, first.state());
        assertSame(first, manager.findByAccount("user01"));

        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.HANDSHAKE_DONE, second.state());
        assertSame(first, manager.findByAccount("user01"));
        assertEquals(0, repository.delegate.metadataUpdateCount());

        repository.allowUpdate.countDown();
        firstLogin.join(1_000);
        close.join(1_000);

        assertFalse(firstLogin.isAlive());
        assertFalse(close.isAlive());
        assertEquals(SessionState.CLOSED, first.state());
        assertEquals(null, manager.findByAccount("user01"));

        newHandler(second, auth).onMessage(loginMessage("user01", "secret1"));

        assertEquals(SessionState.AUTHENTICATED, second.state());
        assertSame(second, manager.findByAccount("user01"));
        assertEquals(2, repository.delegate.metadataUpdateCount());
        assertEquals("198.51.100.2", repository.delegate.requireAccount("user01").ipAddress());
    }

    @Test
    void closesWhenLoginContainsTrailingBytes() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01")
                .writeUtf("secret1").writeByte(1).writeByte(99);

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenUpdateDataContainsTrailingBytes() {
        AuthService auth = TestServices.authService();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = newHandler(session, auth);

        handler.onMessage(new Message(MessageName.UPDATE_DATA, new byte[]{-1, 123}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void closesWhenCreatePlayerContainsTrailingBytes() throws Exception {
        AuthService auth = registeredAuth();
        Session session = newSession(auth);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.bindAccount(1L, "user01");
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        MessageHandler handler = newHandler(session, auth);
        MessageWriter create = new MessageWriter()
                .writeUtf("alpha1")
                .writeByte(0)
                .writeByte(99);

        handler.onMessage(new Message(MessageName.CREATE_PLAYER, create.toByteArray()));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void doesNotSendEmptyFrameDatasetWhenFrameResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{7}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
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
        session.bindPlayer(PlayerProfile.initial(1L, 7, "alpha1", 0));
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

    @Test
    void serializesExactLegacyLevelResource() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();

                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA,
                        new MessageWriter().writeByte(6).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                assertEquals(MessageName.UPDATE_DATA, response.command());
                var reader = response.reader();
                assertEquals(6, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(102, reader.readUnsignedShort());
                for (int id = 0; id < 102; id++) {
                    assertEquals(id, reader.readShort());
                    reader.readUtf();
                    reader.readLong();
                }
                assertEquals(0, reader.remaining());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void serializesMovementEffectResourceInUnityFieldOrder() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(
                    manager.nextId(),
                    new TestTransport(input, output, "127.0.0.1"),
                    manager,
                    new LegacyPacketCodec(262_144),
                    key,
                    4,
                    TestServices.serverServices(TestServices.authService(), resources),
                    NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();

                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA,
                        new MessageWriter().writeByte(3).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()),
                        new LegacyCipher(key),
                        true);

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
            } finally {
                session.close();
            }
        }
    }

    @Test
    void serializesExactLegacyMonsterResourceInUnityFieldOrder() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(4).toByteArray()));

                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                assertEquals(MessageName.UPDATE_DATA, response.command());
                var reader = response.reader();
                assertEquals(4, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(1, reader.readShort());
                assertEquals(0, reader.readShort());
                assertFalse(reader.readBoolean());
                assertEquals(3, reader.readByte());
                assertEquals(2198, reader.readShort());
                assertEquals(2199, reader.readShort());
                assertEquals(2200, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(30, reader.readShort());
                assertEquals(3, reader.readByte());
                assertEquals(2190, reader.readShort());
                assertEquals(2191, reader.readShort());
                assertEquals(2192, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(30, reader.readShort());
                assertEquals(5, reader.readByte());
                assertEquals(2193, reader.readShort());
                assertEquals(2194, reader.readShort());
                assertEquals(2195, reader.readShort());
                assertEquals(2196, reader.readShort());
                assertEquals(2197, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(0, reader.readShort());
                assertEquals(20, reader.readShort());
                assertEquals(1, reader.readShort());
                assertEquals(1, reader.readShort());
                assertEquals("Hổ nanh kiếm", reader.readUtf());
                assertEquals(100, reader.readShort());
                assertEquals(1, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(5, reader.readByte());
                assertEquals(11818, reader.readShort());
                assertEquals(11819, reader.readShort());
                assertEquals(11820, reader.readShort());
                assertEquals(11821, reader.readShort());
                assertEquals(11822, reader.readShort());
                assertEquals(11824, reader.readShort());
                assertEquals(11823, reader.readShort());
                assertEquals(175, reader.readShort());
                assertEquals(95, reader.readShort());
                assertEquals(0, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(0, reader.remaining());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void doesNotSendEmptyMonsterDatasetWhenMonsterResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(new Message(
                MessageName.UPDATE_DATA, new byte[]{4}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void manifestAdvertisesLoadedEffectVersionTwo() throws Exception {
        assertEquals(2, readManifestEffectVersion(ResourceService.fromFrameRoot(
                Path.of("resources", "json"))));
    }

    @Test
    void manifestAdvertisesLoadedMonsterVersionOne() throws Exception {
        assertEquals(1, readManifestMonsterVersion(ResourceService.fromFrameRoot(
                Path.of("resources", "json"))));
    }

    @Test
    void manifestAdvertisesUnavailableMonsterVersionMinusOne() throws Exception {
        assertEquals(-1, readManifestMonsterVersion(ResourceService.unavailable()));
    }

    @Test
    void doesNotSendEmptyEffectDatasetWhenEffectResourcesAreUnavailable() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.UPDATE_DATA, new byte[]{3}));

        assertEquals(0, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void requestIconIsAllowedOnlyAfterHandshake() {
        ResourceService resources = ResourceService.unavailable();

        Session connected = newSession(TestServices.authService());
        newHandler(connected, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CONNECTED, connected.state());

        Session handshakeDone = newSession(TestServices.authService());
        handshakeDone.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        newHandler(handshakeDone, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.HANDSHAKE_DONE, handshakeDone.state());

        Session authenticated = newSession(TestServices.authService());
        authenticated.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        authenticated.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        newHandler(authenticated, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.AUTHENTICATED, authenticated.state());

        Session inGame = newSession(TestServices.authService());
        inGame.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        inGame.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        inGame.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        newHandler(inGame, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.IN_GAME, inGame.state());

        Session closed = newSession(TestServices.authService());
        closed.close();
        newHandler(closed, resources).onMessage(iconRequest(5));
        assertEquals(SessionState.CLOSED, closed.state());
    }

    @Test
    void parsesRequestIconIdAndQueuesAvailableIcon(@TempDir Path root) throws IOException {
        Files.write(root.resolve("5.png"), new byte[]{1, 2, 3});
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.fromIconRoot(root)).onMessage(iconRequest(5));

        assertEquals(1, session.queuedMessages());
        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
    }

    @Test
    void rejectsRequestIconTrailingBytes() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.unavailable()).onMessage(
                new Message(MessageName.REQUEST_ICON, new byte[]{0, 5, 0x7f}));

        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void missingIconDoesNotCloseAuthenticatedSessionOrQueueResponse() {
        Session session = newSession(TestServices.authService());
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);

        MessageHandler handler = newHandler(session, ResourceService.unavailable());
        handler.onMessage(iconRequest(5));

        assertEquals(SessionState.AUTHENTICATED, session.state());
        assertEquals(0, session.queuedMessages());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.AUTHENTICATED, session.state());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.AUTHENTICATED, session.state());
        handler.onMessage(new Message(MessageName.PLAYER_MOVE));
        assertEquals(SessionState.CLOSED, session.state());
    }

    @Test
    void oversizedIconIsNotQueuedPastConfiguredPacketLimit(@TempDir Path root) throws IOException {
        Files.write(root.resolve("5.png"), new byte[70_000]);
        Session session = newSession(TestServices.authService(), 9);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);

        newHandler(session, ResourceService.fromIconRoot(root))
                .onMessage(iconRequest(5));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(0, session.queuedMessages());
    }

    private static MessageHandler newHandler(Session session, AuthService authService) {
        return newHandler(session, TestServices.serverServices(authService, ResourceService.unavailable()),
                NetworkConfig.defaults());
    }

    private static MessageHandler newHandler(Session session, ResourceService resources) {
        return newHandler(session, TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults());
    }

    private static MessageHandler newHandler(Session session, ServerServices services, NetworkConfig config) {
        return new MessageHandler(session, services, config, NetworkEventObserver.NO_OP);
    }

    private static Message iconRequest(int iconId) {
        return new Message(MessageName.REQUEST_ICON,
                new MessageWriter().writeShort(iconId).toByteArray());
    }

    private static Message loginMessage(String username, String password) throws IOException {
        return new Message(MessageName.LOGIN, new MessageWriter()
                .writeUtf("0.9.5")
                .writeUtf(username)
                .writeUtf(password)
                .writeByte(1)
                .toByteArray());
    }

    private static Message moveMessage(int x, int y) {
        return new Message(
                MessageName.PLAYER_MOVE,
                new MessageWriter().writeShort(x).writeShort(y).toByteArray());
    }

    private static Message prepareMonster(int skillId, int monsterId) {
        return new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(skillId).writeByte(1).writeInt(monsterId).toByteArray());
    }

    private static Message monsterImpact(int monsterId) {
        return new Message(
                MessageName.USE_SKILL,
                new MessageWriter().writeByte(1).writeInt(monsterId).toByteArray());
    }

    private static CombatContext combatContext() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        MapService maps = new MapService(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterRuntimeFactory(resources));
        ServerServices services = TestServices.serverServices(TestServices.authService(), resources, maps);
        Session session = inGameSession(services,
                PlayerProfile.initial(1L, 7, "alpha1", 0).withLocation(1, 0, 90, 1008));
        MessageHandler handler = newHandler(session, services, NetworkConfig.defaults());
        maps.finishLoad(session);
        try {
            drainMessages(session);
        } catch (Exception exception) {
            throw new AssertionError("unable to drain combat bootstrap", exception);
        }
        return new CombatContext(session, handler, maps);
    }

    private record CombatContext(Session session, MessageHandler handler, MapService maps) {
    }

    private static AuthService registeredAuth() {
        AuthService auth = TestServices.authService();
        auth.register("user01", "secret1", "127.0.0.1");
        return auth;
    }

    private static Session newSession(AuthService authService) {
        return newSession(authService, 1024);
    }

    private static Session newSession(AuthService authService, int maxPacketSize) {
        return newSession(authService, maxPacketSize, new SessionManager(), "127.0.0.1");
    }

    private static Session newSession(AuthService authService, int maxPacketSize,
                                      SessionManager manager, String remoteAddress) {
        return new Session(manager.nextId(), new TestTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), remoteAddress), manager,
                new LegacyPacketCodec(maxPacketSize), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(authService, ResourceService.unavailable()), NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP);
    }

    private static Session inGameSessionWithPlayer(AuthService auth) {
        Session session = newSession(auth);
        session.bindPlayer(PlayerProfile.initial(1L, 7, "alpha1", 0));
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    private static Session inGameSession(ServerServices services, PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                services, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        session.bindPlayer(player);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        return session;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> drainMessages(Session session) throws Exception {
        Field field = Session.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        BlockingQueue<Message> queue = (BlockingQueue<Message>) field.get(session);
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }

    private static Zone zoneFor(MapService maps, int mapId, int zoneId) throws Exception {
        Field field = MapService.class.getDeclaredField("zones");
        field.setAccessible(true);
        for (Object candidate : ((java.util.Map<?, ?>) field.get(maps)).values()) {
            Zone zone = (Zone) candidate;
            if (zone.mapId() == mapId && zone.zoneId() == zoneId) {
                return zone;
            }
        }
        throw new AssertionError("zone not found map=" + mapId + " zone=" + zoneId);
    }

    private static final class BlockingMetadataRepository implements AccountRepository {
        private final TestAccountRepository delegate = new TestAccountRepository();
        private final CountDownLatch updateEntered = new CountDownLatch(1);
        private final CountDownLatch allowUpdate = new CountDownLatch(1);

        @Override
        public Optional<AccountRecord> findByUsername(String username) {
            return delegate.findByUsername(username);
        }

        @Override
        public long create(String username, byte[] passwordHash, byte[] passwordSalt, String ipAddress) {
            return delegate.create(username, passwordHash, passwordSalt, ipAddress);
        }

        @Override
        public void updateSuccessfulLogin(long accountId, String ipAddress, Instant loginAt) {
            updateEntered.countDown();
            try {
                if (!allowUpdate.await(5, TimeUnit.SECONDS)) {
                    throw new AccountRepositoryException("timed out waiting for test release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AccountRepositoryException("interrupted while waiting for test release", exception);
            }
            delegate.updateSuccessfulLogin(accountId, ipAddress, loginAt);
        }
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.BLOCKED, thread.getState(),
                "map transition did not block on source zone");
    }

    private static void waitForOutput(ByteArrayOutputStream output) throws InterruptedException {
        int lastSize = -1;
        int stableChecks = 0;
        for (int attempt = 0; attempt < 200; attempt++) {
            int size = output.size();
            if (size > 0 && size == lastSize) {
                stableChecks++;
                if (stableChecks >= 3) {
                    return;
                }
            } else {
                stableChecks = 0;
            }
            lastSize = size;
            Thread.sleep(5);
        }
        if (output.size() > 0) {
            return;
        }
        assertTrue(output.size() > 0, "timed out waiting for level resource response");
    }

    private static int readManifestMonsterVersion(ResourceService resources) throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                var reader = response.reader();
                assertEquals(-1, reader.readByte());
                for (int index = 0; index < 5; index++) {
                    reader.readByte();
                }
                int monsterVersion = reader.readByte();
                for (int index = 0; index < 7; index++) {
                    reader.readByte();
                }
                assertEquals(0, reader.remaining());
                return monsterVersion;
            } finally {
                session.close();
            }
        }
    }

    private static int readManifestEffectVersion(ResourceService resources) throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"),
                    manager, new LegacyPacketCodec(262_144), key, 4,
                    TestServices.serverServices(TestServices.authService(), resources), NetworkConfig.defaults(),
                    NetworkEventObserver.NO_OP);
            try {
                session.start();
                session.completeHandshake();
                output.reset();
                newHandler(session, resources).onMessage(new Message(
                        MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
                waitForOutput(output);
                Message response = new LegacyPacketCodec(262_144).readServerResponse(
                        new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(key), true);
                var reader = response.reader();
                assertEquals(-1, reader.readByte());
                for (int index = 0; index < 4; index++) {
                    reader.readByte();
                }
                int effectVersion = reader.readByte();
                for (int index = 0; index < 8; index++) {
                    reader.readByte();
                }
                assertEquals(0, reader.remaining());
                return effectVersion;
            } finally {
                session.close();
            }
        }
    }

    @Test
    void serializesSortedPerIconManifest() throws Exception {
        Path iconRoot = Files.createTempDirectory("icon-manifest-test");
        try {
            byte[] icon2 = new byte[]{4, 5, 6};
            byte[] icon10 = new byte[]{1, 2, 3};
            Files.write(iconRoot.resolve("10.png"), icon10);
            Files.write(iconRoot.resolve("2.png"), icon2);
            ResourceService resources = ResourceService.fromIconRoot(iconRoot, 2);

            PipedInputStream input = new PipedInputStream();
            try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                SessionManager manager = new SessionManager();
                byte[] key = "abc".getBytes(StandardCharsets.US_ASCII);
                Session session = new Session(
                        manager.nextId(),
                        new TestTransport(input, output, "127.0.0.1"),
                        manager,
                        new LegacyPacketCodec(262_144),
                        key,
                        4,
                        TestServices.serverServices(TestServices.authService(), resources),
                        NetworkConfig.defaults(),
                        NetworkEventObserver.NO_OP);
                try {
                    session.start();
                    session.completeHandshake();
                    output.reset();

                    newHandler(session, resources).onMessage(new Message(
                            MessageName.UPDATE_DATA,
                            new MessageWriter().writeByte(12).toByteArray()));

                    waitForOutput(output);
                    Message response = new LegacyPacketCodec(262_144).readServerResponse(
                            new ByteArrayInputStream(output.toByteArray()),
                            new LegacyCipher(key),
                            true);
                    assertEquals(MessageName.UPDATE_DATA, response.command());
                    var reader = response.reader();
                    assertEquals(12, reader.readByte());
                    assertEquals(2, reader.readUnsignedShort());
                    assertEquals(2, reader.readShort());
                    assertEquals(IconFingerprint.fingerprint64(icon2), reader.readLong());
                    assertEquals(10, reader.readShort());
                    assertEquals(IconFingerprint.fingerprint64(icon10), reader.readLong());
                    assertEquals(0, reader.remaining());
                } finally {
                    session.close();
                }
            }
        } finally {
            Files.deleteIfExists(iconRoot.resolve("2.png"));
            Files.deleteIfExists(iconRoot.resolve("10.png"));
            Files.deleteIfExists(iconRoot);
        }
    }
}
