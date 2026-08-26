package com.project.game.map;

import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTest {
    @Test
    void startsEmptyAndTracksBoundPlayer() {
        Zone zone = new Zone(0, 0);
        Session session = session(PlayerProfile.initial("user01", 7, "alpha1", 0));

        assertEquals(0, zone.size());
        assertTrue(zone.add(session));
        assertEquals(1, zone.size());
        assertTrue(zone.containsPlayer(7));
        assertTrue(zone.remove(session));
        assertFalse(zone.containsPlayer(7));
        assertEquals(0, zone.size());
    }

    @Test
    void duplicateSameSessionIsIdempotent() {
        Zone zone = new Zone(0, 0);
        Session session = session(PlayerProfile.initial("user01", 7, "alpha1", 0));

        assertTrue(zone.add(session));
        assertFalse(zone.add(session));
        assertEquals(1, zone.size());
    }

    @Test
    void differentSessionCannotReplaceSamePlayerId() {
        Zone zone = new Zone(0, 0);
        Session first = session(PlayerProfile.initial("user01", 7, "alpha1", 0));
        Session second = session(PlayerProfile.initial("user02", 7, "alpha2", 0));

        assertTrue(zone.add(first));
        assertFalse(zone.add(second));
        assertEquals(List.of(first), zone.snapshot());
    }

    @Test
    void snapshotIsImmutable() {
        Zone zone = new Zone(0, 0);
        zone.add(session(PlayerProfile.initial("user01", 7, "alpha1", 0)));

        List<Session> snapshot = zone.snapshot();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void requiresBoundPlayer() {
        Zone zone = new Zone(0, 0);
        assertThrows(IllegalStateException.class, () -> zone.add(session(null)));
    }

    @Test
    void atomicallyReturnsExistingMembersWhileAddingNewMember() {
        Zone zone = new Zone(0, 0);
        Session first = session(PlayerProfile.initial("user01", 1, "alpha1", 0));
        Session second = session(PlayerProfile.initial("user02", 2, "beta22", 0));
        zone.add(first);

        List<Session> existing = zone.addAndSnapshot(second);

        assertEquals(List.of(first), existing);
        assertEquals(2, zone.size());
        assertTrue(zone.snapshot().containsAll(List.of(first, second)));
    }

    private static Session session(PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                ServerServices.defaults(), NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        if (player != null) {
            session.bindPlayer(player);
        }
        return session;
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return output;
        }

        @Override
        public String remoteAddress() {
            return "zone-test";
        }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}
