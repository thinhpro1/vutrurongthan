package com.project.game.service;

import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.testsupport.GameplayTestSupport;
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.replaceSendQueue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaServiceTest {
    @Test
    void addPlayerExchangesPresenceWithExistingMembers() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session existing = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session joining = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));

        List<Session> rejected = area.addPlayer(joining, joining.player(), List.of(existing));

        assertEquals(List.of(), rejected);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(existing)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(joining)));
    }

    @Test
    void removePlayerNotifiesRemainingMembers() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session leaving = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session remaining = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));

        List<Session> rejected = area.removePlayer(
                leaving, leaving.player().id(), List.of(leaving, remaining));

        assertEquals(List.of(), rejected);
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(drain(remaining)));
        assertEquals(List.of(), drain(leaving));
    }

    @Test
    void moveDoesNotSendBackToMover() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session mover = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session observer = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));
        mover.player().move(1300, 648);

        List<Session> rejected = area.move(mover, mover.player(), List.of(mover, observer));

        assertEquals(List.of(), rejected);
        assertEquals(List.of(), drain(mover));
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(drain(observer)));
    }

    @Test
    void failedTrySendIsReturnedWithoutClosingSession() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session mover = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session observer = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));
        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        replaceSendQueue(observer, fullQueue);

        List<Session> rejected = area.move(mover, mover.player(), List.of(mover, observer));

        assertEquals(List.of(observer), rejected);
        assertSame(observer, rejected.getFirst());
        assertNotEquals(SessionState.CLOSED, observer.state());
    }

    @Test
    void broadcastsMonsterDamageMoveAndRespawnToCurrentMembers() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session first = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session second = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));

        assertEquals(List.of(), area.monsterDamage(
                new Monster.Damage(101, 10, 290, false, 0), List.of(first, second)));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(first)));
        assertEquals(List.of(MessageName.MONSTER_INJURE), commands(drain(second)));

        area.monsterMove(new Monster.Move(101, 979, 936, 1), List.of(first, second));
        area.monsterRespawn(new Monster.Respawn(101, 0, 300), List.of(first, second));
        assertEquals(List.of(MessageName.MONSTER_MOVE, MessageName.MONSTER_RESPAWN),
                commands(drain(first)));
        assertEquals(List.of(MessageName.MONSTER_MOVE, MessageName.MONSTER_RESPAWN),
                commands(drain(second)));
    }

    @Test
    void lethalMonsterAttackSendsAttackBeforeSelfAndObservedDeath() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session victim = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session observer = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));

        List<Session> rejected = area.monsterAttack(
                new Monster.Attack(101, victim.player().id(), 10, 0, true),
                List.of(victim, observer));

        assertEquals(List.of(), rejected);
        assertEquals(List.of(MessageName.MONSTER_ATTACK, MessageName.ME_DIE),
                commands(drain(victim)));
        assertEquals(List.of(MessageName.MONSTER_ATTACK, MessageName.PLAYER_DIE),
                commands(drain(observer)));
    }

    @Test
    void monsterBroadcastSkipsClosedMembersAndReturnsFailedSendsWithoutClosing() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        Session open = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session closed = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));
        closed.transition(SessionState.IN_GAME, SessionState.CLOSED);
        Session full = GameplayTestSupport.session(TestPlayers.initial(3L, 3, "player3", 0));
        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        replaceSendQueue(full, fullQueue);

        List<Session> rejected = area.monsterMove(
                new Monster.Move(101, 979, 936, 1), List.of(open, closed, full));

        assertEquals(List.of(full), rejected);
        assertEquals(List.of(MessageName.MONSTER_MOVE), commands(drain(open)));
        assertEquals(List.of(), drain(closed));
        assertNotEquals(SessionState.CLOSED, full.state());
    }
}
