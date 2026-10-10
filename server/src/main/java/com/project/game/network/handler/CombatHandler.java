package com.project.game.network.handler;

import com.project.game.map.Zone;
import com.project.game.network.Session;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageReader;
import com.project.game.player.Player;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/** Đọc packet chiến đấu rồi chuyển cho Player trên writer của Zone. */
final class CombatHandler {
    private static final int TARGET_PLAYER = 0;
    private static final int TARGET_MONSTER = 1;
    private static final int NO_TARGET = -1;

    private final Session session;
    private final Clock clock;

    CombatHandler(Session session, Clock clock) {
        this.session = session;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Packet -72: chọn skill, có thể kèm mục tiêu. */
    void handleUseSkill(Message message) throws IOException {
        MessageReader reader = message.reader();
        int skillId = reader.readByte();
        int monsterId = -1;
        if (reader.remaining() != 0) {
            if (reader.remaining() != 5) {
                throw new IOException("invalid -72 target payload");
            }
            int targetType = reader.readByte();
            int targetId = reader.readInt();
            if (targetType != TARGET_PLAYER && targetType != TARGET_MONSTER) {
                throw new IOException("unsupported -72 target type " + targetType);
            }
            if (targetType == TARGET_MONSTER) {
                monsterId = targetId;
            }
        }

        Player player = session.player();
        Zone zone = player.zone();
        if (zone == null) {
            return;
        }
        int target = monsterId;
        zone.post(player, () -> player.useSkill(skillId, target));
    }

    /** Packet -108: ra đòn vào mục tiêu đã chọn. */
    void handleAttack(Message message) throws IOException {
        MessageReader reader = message.reader();
        int targetType = reader.readByte();
        int targetId = -1;
        if (targetType == TARGET_PLAYER || targetType == TARGET_MONSTER) {
            if (reader.remaining() != 4) {
                throw new IOException("invalid -108 target payload");
            }
            targetId = reader.readInt();
        } else if (targetType != NO_TARGET) {
            throw new IOException("unsupported -108 target type " + targetType);
        }
        if (reader.remaining() != 0) {
            throw new IOException("trailing -108 payload bytes");
        }

        Player player = session.player();
        Zone zone = player.zone();
        if (zone == null) {
            return;
        }
        int monsterId = targetType == TARGET_MONSTER ? targetId : -1;
        long now = clock.millis();
        zone.post(player, () -> player.attack(monsterId, now));
    }
}
