package com.project.game.network;
import com.project.game.testsupport.TestPlayers;

import com.project.game.testsupport.TestServices;

import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.monster.MonsterManager;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.project.game.network.MessageHandlerTestSupport.*;

class MessageHandlerCombatTest {

    @Test
    void impactWithoutPrepareDoesNotDamageMonster() {
        CombatContext context = combatContext();

        context.handler().onMessage(monsterImpact(101));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void prepareThenImpactAppliesExactlyOneHit() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 101));
        context.handler().onMessage(monsterImpact(101));

        assertEquals(290L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void replayImpactAfterPendingConsumedDoesNotDamageAgain() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 101));
        context.handler().onMessage(monsterImpact(101));
        context.handler().onMessage(monsterImpact(101));

        assertEquals(290L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void mismatchedImpactConsumesPendingAndDoesNotRetainIt() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 101));
        context.handler().onMessage(monsterImpact(102));
        context.handler().onMessage(monsterImpact(101));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void oneBytePrepareClearsPending() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 101));
        context.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).toByteArray()));
        context.handler().onMessage(monsterImpact(101));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
    }

    @Test
    void playerTargetPrepareIsValidNoOp() {
        CombatContext context = combatContext();

        context.handler().onMessage(new Message(
                MessageName.PLAYER_START_USE_ULTIMATE,
                new MessageWriter().writeByte(7).writeByte(0).writeInt(99).toByteArray()));
        context.handler().onMessage(monsterImpact(101));

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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices maps = new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterManager(resources));
        SessionServices services = TestServices.serverServices(TestServices.auth(), resources, maps);
        Session session = inGameSession(services,
                TestPlayers.at(TestPlayers.initial(1L, 7, "alpha1", 0), 1, 0, 90, 1008));
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());
        maps.monsterSnapshots(1, 0);

        handler.onMessage(prepareMonster(7, 101));
        handler.onMessage(monsterImpact(101));

        assertEquals(300L, maps.monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void mapChangeClearsPendingMonsterAttack() {
        CombatContext context = combatContext();

        context.handler().onMessage(prepareMonster(7, 101));
        context.maps().findZone(1, 0).call(() -> {
            context.session().player().changeMap(1, 0, 0, 1008);
            return null;
        });
        context.handler().onMessage(new Message(MessageName.REQUEST_CHANGE_MAP));
        context.handler().onMessage(monsterImpact(101));

        assertEquals(300L, context.maps().monsterSnapshots(1, 0).getFirst().hp());
        assertEquals(0, context.session().player().mapId());
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
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        GameplayServices maps = new GameplayServices(new PlayerPacketWriter(), new MonsterPacketWriter(),
                new MonsterManager(resources));
        SessionServices services = TestServices.serverServices(TestServices.auth(), resources, maps);
        Session session = inGameSession(services,
                TestPlayers.at(TestPlayers.initial(1L, 7, "alpha1", 0), 1, 0, 90, 1008));
        MessageHandler handler = newHandler(session, services, ClientConfig.defaults());
        maps.finishLoad(session);
        try {
            drainMessages(session);
        } catch (Exception exception) {
            throw new AssertionError("unable to drain combat bootstrap", exception);
        }
        return new CombatContext(session, handler, maps);
    }

    private record CombatContext(Session session, MessageHandler handler, GameplayServices maps) {
    }
}
