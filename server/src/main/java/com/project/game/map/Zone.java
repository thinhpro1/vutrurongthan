package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.player.PlayerProfile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent runtime membership for one map zone. */
public final class Zone {
    private final int mapId;
    private final int zoneId;
    private final ConcurrentHashMap<Integer, Session> members = new ConcurrentHashMap<>();

    public Zone(int mapId, int zoneId) {
        this.mapId = mapId;
        this.zoneId = zoneId;
    }

    public int mapId() {
        return mapId;
    }

    public int zoneId() {
        return zoneId;
    }

    public boolean add(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        return members.putIfAbsent(player.id(), session) == null;
    }

    public boolean remove(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        return members.remove(player.id(), session);
    }

    public boolean containsPlayer(int playerId) {
        return members.containsKey(playerId);
    }

    public int size() {
        return members.size();
    }

    public List<Session> snapshot() {
        return List.copyOf(members.values());
    }

    private static PlayerProfile requirePlayer(Session session) {
        PlayerProfile player = session.player();
        if (player == null) {
            throw new IllegalStateException("zone membership requires a bound player");
        }
        return player;
    }
}
