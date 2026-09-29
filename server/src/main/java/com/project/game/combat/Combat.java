package com.project.game.combat;

import com.project.game.map.Zone;
import com.project.game.network.Session;
import com.project.game.network.SessionState;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Routes combat requests to the owning Zone. */
public final class Combat {
    private final Clock clock;

    public Combat(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean canTargetMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.zone() == null) {
            return false;
        }
        Zone zone = session.zone();
        try {
            return zone.canTargetMonster(session, monsterId);
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public boolean attackMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.zone() == null) {
            return false;
        }
        Zone zone = session.zone();
        try {
            return zone.attackMonster(session, monsterId, clock.millis());
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }
}
