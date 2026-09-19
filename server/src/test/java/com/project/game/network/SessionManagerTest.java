package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {
    @Test
    void bindAccountAssociatesSessionAndDisconnectAllowsRelogin() {
        SessionManager manager = new SessionManager();
        Session first = newSession(manager);
        Session second = newSession(manager);

        assertTrue(manager.beginAccountAdmission(first, 1L, "user01"));
        manager.finishAccountAdmission(first, true);
        assertSame(first, manager.findByAccount("user01"));
        first.close();

        assertNull(manager.findByAccount("user01"));
        assertTrue(manager.beginAccountAdmission(second, 1L, "user01"));
        manager.finishAccountAdmission(second, true);
        assertSame(second, manager.findByAccount("user01"));
    }

    @Test
    void bindAccountRejectsClosedSessionWithoutLeavingAccountEntry() {
        SessionManager manager = new SessionManager();
        Session session = newSession(manager);
        session.close();

        assertFalse(manager.beginAccountAdmission(session, 1L, "user01"));
        assertNull(manager.findByAccount("user01"));
    }

    @Test
    void admissionBindsPersistentAccountIdAndNameTogether() {
        SessionManager manager = new SessionManager();
        Session session = newSession(manager);

        assertTrue(manager.beginAccountAdmission(session, 42L, "user01"));
        assertEquals(42L, session.accountId());
        assertEquals("user01", session.accountName());
        assertSame(session, manager.findByAccount("user01"));
        manager.finishAccountAdmission(session, true);
    }

    private static Session newSession(SessionManager manager) {
        return new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
    }
}
