package com.project.game.service;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
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
        AreaService area = new AreaService(new PlayerPacketWriter());
        Session existing = GameplayTestSupport.session(TestPlayers.initial(1L, 1, "player1", 0));
        Session joining = GameplayTestSupport.session(TestPlayers.initial(2L, 2, "player2", 0));

        List<Session> rejected = area.addPlayer(joining, joining.player(), List.of(existing));

        assertEquals(List.of(), rejected);
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(existing)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(joining)));
    }

    @Test
    void removePlayerNotifiesRemainingMembers() throws Exception {
        AreaService area = new AreaService(new PlayerPacketWriter());
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
        AreaService area = new AreaService(new PlayerPacketWriter());
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
        AreaService area = new AreaService(new PlayerPacketWriter());
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
}
