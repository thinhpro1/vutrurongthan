package com.project.game.network.handler;

import com.project.game.combat.Combat;
import com.project.game.network.Session;
import com.project.game.network.message.Message;

import java.io.IOException;

/** Phân tích lệnh chiến đấu và quản lý đòn đánh quái đang chờ ở protocol. */
final class CombatHandler {
    private record PendingMonsterAttack(int skillId, int monsterId) {
    }

    private final Session session;
    private final Combat combat;
    private PendingMonsterAttack pendingMonsterAttack;

    CombatHandler(Session session, Combat combat) {
        this.session = session;
        this.combat = combat;
    }

    void clearPendingAttack() {
        pendingMonsterAttack = null;
    }

    void handlePrepareMonsterAttack(Message message) throws IOException {
        pendingMonsterAttack = null;

        var reader = message.reader();
        int skillId = reader.readByte();

        if (reader.remaining() == 0) {
            return;
        }

        if (reader.remaining() != 5) {
            throw new IOException("invalid -72 target payload");
        }

        int targetType = reader.readByte();
        int targetId = reader.readInt();

        if (reader.remaining() != 0) {
            throw new IOException("trailing -72 payload bytes");
        }

        if (targetType == 0) {
            return;
        }

        if (targetType != 1) {
            throw new IOException("unsupported -72 target type " + targetType);
        }

        if (session.player() == null || !combat.canTargetMonster(session, targetId)) {
            return;
        }

        pendingMonsterAttack = new PendingMonsterAttack(skillId, targetId);
    }

    void handleMonsterAttackImpact(Message message) throws IOException {
        var reader = message.reader();
        int targetType = reader.readByte();
        int targetId = -1;

        switch (targetType) {
            case -1 -> {
                if (reader.remaining() != 0) {
                    throw new IOException("trailing -108 no-target payload");
                }
            }
            case 0, 1 -> {
                if (reader.remaining() != 4) {
                    throw new IOException("invalid -108 target payload");
                }
                targetId = reader.readInt();
            }
            default -> throw new IOException("unsupported -108 target type " + targetType);
        }

        if (reader.remaining() != 0) {
            throw new IOException("trailing -108 payload bytes");
        }

        PendingMonsterAttack pending = pendingMonsterAttack;
        pendingMonsterAttack = null;

        if (targetType != 1
                || pending == null
                || pending.monsterId() != targetId) {
            return;
        }

        combat.attackMonster(session, targetId);
    }
}
