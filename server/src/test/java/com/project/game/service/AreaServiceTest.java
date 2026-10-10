package com.project.game.service;

import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.Player;
import com.project.game.testsupport.TestZone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.replaceSendQueue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaServiceTest {
    @Test
    void joiningPlayerAndExistingMembersSeeEachOther() throws Exception {
        TestZone area = new TestZone();
        Player existing = area.join(1, 975, 936);

        area.join(2, 975, 936);

        assertEquals(List.of(MessageName.ADD_PLAYER), area.commands(existing));
    }

    @Test
    void leavingNotifiesRemainingMembersOnly() throws Exception {
        TestZone area = new TestZone();
        Player leaving = area.join(1, 975, 936);
        Player remaining = area.join(2, 975, 936);
        area.commands(leaving);
        area.commands(remaining);

        area.zone().leave(leaving);

        assertEquals(List.of(MessageName.REMOVE_PLAYER), area.commands(remaining));
        assertEquals(List.of(), area.commands(leaving));
    }

    @Test
    void moveIsNotSentBackToMover() throws Exception {
        TestZone area = new TestZone();
        Player mover = area.join(1, 975, 936);
        Player observer = area.join(2, 975, 936);
        area.commands(mover);
        area.commands(observer);

        area.runOnWriter(() -> area.zone().service().playerMove(mover));

        assertEquals(List.of(), area.commands(mover));
        assertEquals(List.of(MessageName.PLAYER_MOVE), area.commands(observer));
    }

    @Test
    void fullSendQueueKicksTheReceiverAfterLeavingTheWriter() throws Exception {
        TestZone area = new TestZone();
        Player mover = area.join(1, 975, 936);
        Player observer = area.join(2, 975, 936);
        fillSendQueue(observer.session());

        area.runOnWriter(() -> area.zone().service().playerMove(mover));

        assertEquals(SessionState.CLOSED, observer.session().state());
        assertNotEquals(SessionState.CLOSED, mover.session().state());
    }

    @Test
    void broadcastsMonsterInjureMoveAndRespawnToMembers() throws Exception {
        TestZone area = new TestZone();
        Player first = area.join(1, 975, 936);
        Player second = area.join(2, 975, 936);
        area.commands(first);
        area.commands(second);
        Monster monster = area.monster(101);

        area.runOnWriter(() -> {
            area.zone().service().monsterInjure(monster, 10);
            area.zone().service().monsterMove(monster);
            area.zone().service().monsterRespawn(monster);
        });

        List<Integer> expected = List.of(
                MessageName.MONSTER_INJURE, MessageName.MONSTER_MOVE, MessageName.MONSTER_RESPAWN);
        assertEquals(expected, area.commands(first));
        assertEquals(expected, area.commands(second));
    }

    @Test
    void lethalMonsterAttackSendsAttackBeforeSelfAndObservedDeath() throws Exception {
        TestZone area = new TestZone();
        Player victim = area.join(1, 975, 936);
        Player observer = area.join(2, 975, 936);
        area.commands(victim);
        area.commands(observer);
        Monster monster = area.monster(101);

        area.runOnWriter(() -> {
            victim.injure(Long.MAX_VALUE);
            area.zone().service().monsterAttack(monster, victim, 10);
        });

        assertEquals(List.of(MessageName.MONSTER_ATTACK, MessageName.ME_DIE), area.commands(victim));
        assertEquals(List.of(MessageName.MONSTER_ATTACK, MessageName.PLAYER_DIE),
                area.commands(observer));
    }

    @Test
    void monsterBroadcastSkipsClosedMembersAndKicksFullOnes() throws Exception {
        TestZone area = new TestZone();
        Player open = area.join(1, 975, 936);
        Player closed = area.join(2, 975, 936);
        Player full = area.join(3, 975, 936);
        area.commands(open);
        area.commands(closed);
        closed.session().transition(SessionState.IN_GAME, SessionState.CLOSED);
        fillSendQueue(full.session());
        Monster monster = area.monster(101);

        area.runOnWriter(() -> area.zone().service().monsterMove(monster));

        // "full" bị kick rồi rời Zone, nên "open" nhận thêm REMOVE_PLAYER sau gói di chuyển.
        assertEquals(MessageName.MONSTER_MOVE, (int) area.commands(open).getFirst());
        assertEquals(List.of(), drain(closed.session()));
        assertEquals(SessionState.CLOSED, full.session().state());
    }

    private static void fillSendQueue(Session session) throws Exception {
        ArrayBlockingQueue<Message> fullQueue = new ArrayBlockingQueue<>(1);
        assertTrue(fullQueue.offer(new Message(MessageName.DIALOG_OK)));
        replaceSendQueue(session, fullQueue);
    }
}
