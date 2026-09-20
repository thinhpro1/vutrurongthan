package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.util.Objects;
import java.util.Optional;

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

    public void finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return;
        }
        PlayerProfile joining = session.player();
        if (joining == null) {
            return;
        }
        Zone zone = zones.getOrCreate(joining.mapId(), joining.zoneId());
        synchronized (zone) {
            var existing = zone.addAndSnapshot(session);
            if (existing == null) {
                return;
            }
            for (Session member : existing) {
                if (member == session || member.state() == SessionState.CLOSED || member.player() == null) {
                    continue;
                }
                session.send(packets.addPlayer(member.player()));
                if (session.state() != SessionState.CLOSED) {
                    member.send(packets.addPlayer(joining));
                }
            }
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
        synchronized (zone) {
            if (!zone.remove(session)) {
                return;
            }
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED) {
                    member.send(packets.removePlayer(leaving.id()));
                }
            }
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

            boolean removed = sourceZone.remove(session);
            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID, DEATH_RETURN_X, DEATH_RETURN_Y);
            session.bindPlayer(revived);

            if (removed) {
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

            boolean removed = sourceZone.remove(session);
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);

            if (removed) {
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
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != observed.mapId() || current.zoneId() != observed.zoneId()) {
                return false;
            }
            session.bindPlayer(current.withPosition(x, y));
            return true;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != observed.mapId() || current.zoneId() != observed.zoneId()) {
                return false;
            }

            boolean currentMember = zone.contains(session);
            PlayerProfile moved = current.withPosition(x, y);
            session.bindPlayer(moved);
            if (!currentMember) {
                return true;
            }

            Message packet = packets.movePlayer(moved.id(), moved.x(), moved.y());
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED) {
                    member.send(packet);
                }
            }
            return true;
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = zones.find(mapId, zoneId);
        return zone == null ? 0 : zone.size();
    }
}
