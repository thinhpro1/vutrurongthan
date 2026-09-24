package com.project.game.combat;

import com.project.game.monster.Monster;

import com.project.game.map.Zone;
import com.project.game.map.MapManager;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.time.Clock;
import java.util.Objects;

/** Coordinates player-to-monster targeting, damage, broadcasts, and rewards. */
public final class CombatService {
    private final MapManager maps;
    private final PlayerPacketWriter packets;
    private final MonsterPacketWriter monsterPackets;
    private final Clock clock;

    public CombatService(MapManager maps,
                         PlayerPacketWriter packets,
                         MonsterPacketWriter monsterPackets) {
        this(maps, packets, monsterPackets, Clock.systemUTC());
    }

    public CombatService(MapManager maps,
                         PlayerPacketWriter packets,
                         MonsterPacketWriter monsterPackets,
                         Clock clock) {
        this.maps = Objects.requireNonNull(maps, "maps");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean canTargetMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        PlayerProfile player = session.player();
        if (player == null) {
            return false;
        }

        Zone zone = maps.findZone(player.mapId(), player.zoneId());
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            return current != null
                    && current.hp() > 0L
                    && zone.hasPlayer(session)
                    && zone.hasLiveMonster(monsterId);
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

        Zone zone = maps.findZone(player.mapId(), player.zoneId());
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !zone.hasPlayer(session)) {
                return false;
            }

            var result = zone.damageMonster(
                    monsterId, current.id(), damage, clock.millis());
            if (result.isEmpty()) {
                return false;
            }

            Monster.Damage combat = result.orElseThrow();
            PlayerProfile rewarded = null;
            if (combat.killed() && combat.potentialReward() > 0L) {
                PlayerProfile rewardCurrent = session.player();
                if (rewardCurrent == null || !zone.hasPlayer(session)) {
                    throw new IllegalStateException(
                            "killer left authoritative zone during serialized attack");
                }
                long potentialAfter = saturatingAddNonNegative(
                        rewardCurrent.potential(), combat.potentialReward());
                rewarded = rewardCurrent.withPotential(potentialAfter);
                session.bindPlayer(rewarded);
            }

            Message packet = combat.killed()
                    ? monsterPackets.startDie(combat)
                    : monsterPackets.injure(combat);

            for (Session member : zone.players()) {
                if (member.state() != SessionState.CLOSED) {
                    member.send(packet);
                }
            }

            if (rewarded != null && session.state() != SessionState.CLOSED) {
                session.send(packets.potentialUpdate(rewarded.potential()));
            }

            return true;
        }
    }

    private static long saturatingAddNonNegative(long current, long delta) {
        if (current < 0L || delta < 0L) {
            throw new IllegalArgumentException("values must be non-negative");
        }
        if (current > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }
}
