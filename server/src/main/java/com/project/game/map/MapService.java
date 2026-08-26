package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates presence and movement within the current map/zone keys. */
public final class MapService {
    private record ZoneKey(int mapId, int zoneId) {
    }

    private final PlayerPacketWriter packets;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();

    public MapService(PlayerPacketWriter packets) {
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
        Zone zone = zones.computeIfAbsent(keyOf(joining), key -> new Zone(key.mapId(), key.zoneId()));
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

    public void leave(Session session) {
        if (session == null) {
            return;
        }
        PlayerProfile leaving = session.player();
        if (leaving == null) {
            return;
        }
        ZoneKey key = keyOf(leaving);
        Zone zone = zones.get(key);
        if (zone == null || !zone.remove(session)) {
            return;
        }
        for (Session member : zone.snapshot()) {
            if (member != session && member.state() != SessionState.CLOSED) {
                member.send(packets.removePlayer(leaving.id()));
            }
        }
    }

    public void playerMoved(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return;
        }
        PlayerProfile moved = session.player();
        if (moved == null) {
            return;
        }
        Zone zone = zones.get(keyOf(moved));
        if (zone == null) {
            return;
        }
        var members = zone.snapshot();
        if (members.stream().noneMatch(member -> member == session)) {
            return;
        }
        for (Session member : members) {
            if (member != session && member.state() != SessionState.CLOSED) {
                member.send(packets.movePlayer(moved.id(), moved.x(), moved.y()));
            }
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = zones.get(new ZoneKey(mapId, zoneId));
        return zone == null ? 0 : zone.size();
    }

    private static ZoneKey keyOf(PlayerProfile player) {
        return new ZoneKey(player.mapId(), player.zoneId());
    }
}
