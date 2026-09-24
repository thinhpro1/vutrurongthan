package com.project.game.combat;

import com.project.game.map.MapManager;
import com.project.game.map.Zone;
import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Điều phối việc Player đánh Monster; Player sở hữu chuyển trạng thái của mình. */
public final class Combat {
    private final MapManager maps;
    private final PlayerPacketWriter packets;
    private final MonsterPacketWriter monsterPackets;
    private final Clock clock;

    public Combat(MapManager maps,
                         PlayerPacketWriter packets,
                         MonsterPacketWriter monsterPackets) {
        this(maps, packets, monsterPackets, Clock.systemUTC());
    }

    public Combat(MapManager maps,
                         PlayerPacketWriter packets,
                         MonsterPacketWriter monsterPackets,
                         Clock clock) {
        this.maps = Objects.requireNonNull(maps, "maps");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean canTargetMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.player() == null || session.zone() == null) {
            return false;
        }
        Zone zone = session.zone();
        try {
            return zone.tryCall(() -> {
                synchronized (zone) {
                    Player player = session.player();
                    return player != null && !player.isDead()
                            && zone.hasPlayer(session)
                            && zone.hasLiveMonster(monsterId);
                }
            });
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public boolean attackMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.player() == null || session.zone() == null) {
            return false;
        }
        Zone zone = session.zone();
        try {
            AttackDelivery result = zone.tryCall(() -> {
                synchronized (zone) {
                    Player player = session.player();
                    if (player == null || player.isDead() || !zone.hasPlayer(session)
                            || player.currentStats().damage() <= 0) {
                        return new AttackDelivery(false, List.of());
                    }

                    var damageResult = zone.damageMonster(
                            monsterId, player.id(), player.currentStats().damage(), clock.millis());
                    if (damageResult.isEmpty()) {
                        return new AttackDelivery(false, List.of());
                    }

                    Monster.Damage combat = damageResult.orElseThrow();
                    List<Session> rejected = new ArrayList<>();
                    Message packet = combat.killed()
                            ? monsterPackets.startDie(combat)
                            : monsterPackets.injure(combat);
                    for (Session member : zone.members()) {
                        if (member.state() != SessionState.CLOSED && !member.trySend(packet)) {
                            rejected.add(member);
                        }
                    }

                    if (combat.killed() && combat.potentialReward() > 0L) {
                        long potential = player.addPotential(combat.potentialReward());
                        if (session.state() != SessionState.CLOSED
                                && !session.trySend(packets.potentialUpdate(potential))) {
                            rejected.add(session);
                        }
                    }
                    return new AttackDelivery(true, rejected);
                }
            });
            closeRejected(result.rejectedObservers());
            return result.attacked();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private static void closeRejected(List<Session> sessions) {
        for (Session session : sessions) {
            session.close();
        }
    }

    private record AttackDelivery(boolean attacked, List<Session> rejectedObservers) {
        private AttackDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }
}
