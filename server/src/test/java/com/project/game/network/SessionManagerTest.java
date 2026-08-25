package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {
    @Test
    void bindAccountAssociatesSessionAndDisconnectAllowsRelogin() {
        SessionManager manager = new SessionManager();
        Session first = newSession(manager);
        Session second = newSession(manager);

        assertTrue(manager.bindAccount(first, "user01"));
        assertSame(first, manager.findByAccount("user01"));
        first.close();

        assertNull(manager.findByAccount("user01"));
        assertTrue(manager.bindAccount(second, "user01"));
        assertSame(second, manager.findByAccount("user01"));
    }

    @Test
    void bindAccountRejectsClosedSessionWithoutLeavingAccountEntry() {
        SessionManager manager = new SessionManager();
        Session session = newSession(manager);
        session.close();

        assertFalse(manager.bindAccount(session, "user01"));
        assertNull(manager.findByAccount("user01"));
    }

    private static Session newSession(SessionManager manager) {
        return new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                ServerServices.defaults(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
    }
}
