package com.project.game.combat;

import com.project.game.map.Zone;
import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;
import com.project.game.service.AreaService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates Player damage against a Monster in the owning Zone writer. */
public final class Combat {
    private final AreaService area;
    private final PlayerPacketWriter playerPackets;
    private final Clock clock;

    public Combat(AreaService area, PlayerPacketWriter playerPackets) {
        this(area, playerPackets, Clock.systemUTC());
    }

    public Combat(AreaService area, PlayerPacketWriter playerPackets, Clock clock) {
        this.area = Objects.requireNonNull(area, "area");
        this.playerPackets = Objects.requireNonNull(playerPackets, "playerPackets");
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
            AttackDelivery delivery = zone.tryCall(() -> {
                synchronized (zone) {
                    Player player = session.player();
                    if (player == null || player.isDead() || !zone.hasPlayer(session)
                            || player.currentStats().damage() <= 0L) {
                        return new AttackDelivery(false, List.of());
                    }

                    Monster.Damage result = zone.damageMonster(
                            monsterId, player.id(), player.currentStats().damage(), clock.millis());
                    if (result == null) {
                        return new AttackDelivery(false, List.of());
                    }

                    List<Session> rejected = new ArrayList<>(
                            area.monsterDamage(result, zone.members()));
                    if (result.killed() && result.potentialReward() > 0L) {
                        long potential = player.addPotential(result.potentialReward());
                        if (session.state() != SessionState.CLOSED
                                && !session.trySend(playerPackets.potentialUpdate(potential))) {
                            rejected.add(session);
                        }
                    }
                    return new AttackDelivery(true, rejected);
                }
            });
            closeRejected(delivery.rejectedObservers());
            return delivery.attacked();
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
