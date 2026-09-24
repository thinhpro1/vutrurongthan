package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates presence and movement within the current map/zone keys. */
public final class MapService {
    private static final int DEATH_RETURN_MAP_ID = 0;
    private static final int DEATH_RETURN_ZONE_ID = 0;
    private static final int DEATH_RETURN_X = 1250;
    private static final int DEATH_RETURN_Y = 648;

    private final ZoneRegistry zones;
    private final PlayerPacketWriter packets;

    public MapService(ZoneRegistry zones, PlayerPacketWriter packets) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    public boolean finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        PlayerProfile joining = session.player();
        if (joining == null) {
            return false;
        }
        Zone zone;
        try {
            zone = zones.getOrCreate(joining.mapId(), joining.zoneId());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        try {
            Join result = zone.tryCall(() -> finishLoadInZone(zone, session));
            closeRejected(result.rejectedObservers());
            return result.accepted();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public void leave(Session session) {
        if (session == null) {
            return;
        }
        PlayerProfile leaving = session.player();
        if (leaving == null) {
            return;
        }
        Zone zone = zones.find(leaving.mapId(), leaving.zoneId());
        if (zone == null) {
            return;
        }
        try {
            Leave result = zone.call(() -> leaveInZone(zone, session, leaving));
            closeRejected(result.rejectedObservers());
        } catch (RejectedExecutionException exception) {
            // A stopped Zone cannot accept more authoritative work.
        }
    }

    public Optional<PlayerProfile> returnTownFromDeath(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return Optional.empty();
        }

        PlayerProfile observed = session.player();
        if (observed == null || observed.hp() > 0L) {
            return Optional.empty();
        }

        Zone townZone = resolveZone(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID);
        if (townZone == null || !canAccept(townZone, session)) {
            return Optional.empty();
        }

        Zone sourceZone = zones.find(observed.mapId(), observed.zoneId());
        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() > 0L
                    || current.mapId() != observed.mapId()
                    || current.zoneId() != observed.zoneId()) {
                return Optional.empty();
            }
            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID, DEATH_RETURN_X, DEATH_RETURN_Y);
            session.bindPlayer(revived);
            return Optional.of(revived);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() > 0L
                    || current.mapId() != observed.mapId()
                    || current.zoneId() != observed.zoneId()) {
                return Optional.empty();
            }

            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID, DEATH_RETURN_X, DEATH_RETURN_Y);
            if (sourceZone == townZone) {
                session.bindPlayer(revived);
                return Optional.of(revived);
            }

            boolean removed = sourceZone.remove(session);
            session.bindPlayer(revived);

            if (removed && sourceZone != townZone) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.snapshot()) {
                    if (member != session && member.state() != SessionState.CLOSED) {
                        member.send(packet);
                    }
                }
            }
            return Optional.of(revived);
        }
    }

    public Optional<PlayerProfile> changeMap(
            Session session,
            int expectedMapId,
            int expectedZoneId,
            int destinationMapId,
            int destinationZoneId,
            int destinationX,
            int destinationY) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return Optional.empty();
        }
        if (session.player() == null) {
            return Optional.empty();
        }

        Zone destinationZone = resolveZone(destinationMapId, destinationZoneId);
        if (destinationZone == null || !canAccept(destinationZone, session)) {
            return Optional.empty();
        }

        Zone sourceZone = zones.find(expectedMapId, expectedZoneId);
        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != expectedMapId || current.zoneId() != expectedZoneId) {
                return Optional.empty();
            }
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);
            return Optional.of(changed);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != expectedMapId || current.zoneId() != expectedZoneId) {
                return Optional.empty();
            }

            if (sourceZone == destinationZone) {
                PlayerProfile changed = current.withLocation(
                        destinationMapId, destinationZoneId, destinationX, destinationY);
                session.bindPlayer(changed);
                return Optional.of(changed);
            }

            boolean removed = sourceZone.remove(session);
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);

            if (removed && sourceZone != destinationZone) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.snapshot()) {
                    if (member != session && member.state() != SessionState.CLOSED) {
                        member.send(packet);
                    }
                }
            }
            return Optional.of(changed);
        }
    }

    public boolean movePlayer(Session session, int x, int y) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        PlayerProfile observed = session.player();
        if (observed == null) {
            return false;
        }
        Zone zone = zones.find(observed.mapId(), observed.zoneId());
        if (zone == null) {
            return false;
        }

        try {
            MoveResult result = zone.tryCall(() -> movePlayerInZone(zone, session, observed, x, y));
            closeRejected(result.rejectedObservers());
            return result.moved();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private Join finishLoadInZone(Zone zone, Session session) {
        synchronized (zone) {
            PlayerProfile joining = session.player();
            if (joining == null) {
                return new Join(false, List.of());
            }

            Zone.JoinResult result = zone.addAndSnapshot(session);
            if (result.status() == Zone.JoinStatus.FULL
                    || result.status() == Zone.JoinStatus.PLAYER_ID_CONFLICT) {
                return new Join(false, List.of());
            }
            if (result.status() == Zone.JoinStatus.ALREADY_PRESENT) {
                return new Join(true, List.of());
            }

            List<Session> rejectedObservers = new ArrayList<>();
            for (Session member : result.existing()) {
                if (member == session || member.state() == SessionState.CLOSED || member.player() == null) {
                    continue;
                }
                if (!session.trySend(packets.addPlayer(member.player()))) {
                    rejectedObservers.add(session);
                    return new Join(true, rejectedObservers);
                }
                if (!member.trySend(packets.addPlayer(joining))) {
                    rejectedObservers.add(member);
                }
            }
            return new Join(true, rejectedObservers);
        }
    }

    private Leave leaveInZone(Zone zone, Session session, PlayerProfile leaving) {
        synchronized (zone) {
            if (!zone.remove(session)) {
                return new Leave(List.of());
            }

            Message packet = packets.removePlayer(leaving.id());
            List<Session> rejectedObservers = new ArrayList<>();
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED
                        && !member.trySend(packet)) {
                    rejectedObservers.add(member);
                }
            }
            return new Leave(rejectedObservers);
        }
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    private MoveResult movePlayerInZone(
            Zone zone,
            Session session,
            PlayerProfile observed,
            int x,
            int y) {
        synchronized (zone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != observed.mapId() || current.zoneId() != observed.zoneId()) {
                return new MoveResult(false, List.of());
            }

            boolean currentMember = zone.contains(session);
            PlayerProfile moved = current.withPosition(x, y);
            session.bindPlayer(moved);
            if (!currentMember) {
                return new MoveResult(true, List.of());
            }

            Message packet = packets.movePlayer(moved.id(), moved.x(), moved.y());
            List<Session> rejectedObservers = new ArrayList<>();
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED) {
                    if (!member.trySend(packet)) {
                        rejectedObservers.add(member);
                    }
                }
            }
            return new MoveResult(true, rejectedObservers);
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = zones.find(mapId, zoneId);
        return zone == null ? 0 : zone.size();
    }

    /** Resolves a policy-valid normal zone before a handler emits map state. */
    public boolean ensureZone(int mapId, int zoneId) {
        return resolveZone(mapId, zoneId) != null;
    }

    private Zone resolveZone(int mapId, int zoneId) {
        try {
            return zones.getOrCreate(mapId, zoneId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean canAccept(Zone zone, Session session) {
        synchronized (zone) {
            return zone.canAccept(session);
        }
    }

    private record MoveResult(boolean moved, List<Session> rejectedObservers) {
        private MoveResult {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record Join(boolean accepted, List<Session> rejectedObservers) {
        private Join {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record Leave(List<Session> rejectedObservers) {
        private Leave {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }
}
