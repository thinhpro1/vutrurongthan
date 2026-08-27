package com.project.game.map;

import com.project.game.monster.MonsterDamageResult;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.message.Message;
import com.project.game.player.PlayerProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates presence and movement within the current map/zone keys. */
public final class MapService {
    private record ZoneKey(int mapId, int zoneId) {
    }

    private final PlayerPacketWriter packets;
    private final MonsterPacketWriter monsterPackets;
    private final MonsterRuntimeFactory monsterFactory;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();

    public MapService(PlayerPacketWriter packets,
                      MonsterPacketWriter monsterPackets,
                      MonsterRuntimeFactory monsterFactory) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
    }

    public boolean canTargetMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        PlayerProfile player = session.player();
        if (player == null) {
            return false;
        }

        Zone zone = zones.get(keyOf(player));
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            return zone.contains(session) && zone.hasLiveMonster(monsterId);
        }
    }

    public boolean attackMonster(Session session, int monsterId, long damage) {
        if (session == null
                || session.state() == SessionState.CLOSED
                || damage <= 0) {
            return false;
        }

        PlayerProfile player = session.player();
        if (player == null) {
            return false;
        }

        Zone zone = zones.get(keyOf(player));
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            if (!zone.contains(session)) {
                return false;
            }

            Optional<MonsterDamageResult> result = zone.damageMonster(monsterId, damage);
            if (result.isEmpty()) {
                return false;
            }

            MonsterDamageResult combat = result.orElseThrow();
            Message packet = combat.killed()
                    ? monsterPackets.startDie(combat)
                    : monsterPackets.injure(combat);

            for (Session member : zone.snapshot()) {
                if (member.state() != SessionState.CLOSED) {
                    member.send(packet);
                }
            }

            return true;
        }
    }

    public void finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return;
        }
        PlayerProfile joining = session.player();
        if (joining == null) {
            return;
        }
        Zone zone = getOrCreateZone(joining.mapId(), joining.zoneId());
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
        ZoneKey key = keyOf(leaving);
        Zone zone = zones.get(key);
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

    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        return getOrCreateZone(mapId, zoneId).monsterSnapshots();
    }

    private Zone getOrCreateZone(int mapId, int zoneId) {
        return zones.computeIfAbsent(
                new ZoneKey(mapId, zoneId),
                key -> new Zone(
                        key.mapId(),
                        key.zoneId(),
                        monsterFactory.createForMap(key.mapId())));
    }

    private static ZoneKey keyOf(PlayerProfile player) {
        return new ZoneKey(player.mapId(), player.zoneId());
    }
}
