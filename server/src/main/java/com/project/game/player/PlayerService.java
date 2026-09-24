package com.project.game.player;

import com.project.game.persistence.player.DuplicatePlayerException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.PlayerProfileFactory;
import com.project.game.player.PlayerProfile;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Account-scoped durable player service; realtime state remains in Session/MapManager. */
public final class PlayerService {
    private static final Logger LOGGER = Logger.getLogger(PlayerService.class.getName());
    private static final String SYSTEM_BUSY = "Hệ thống đang bận, vui lòng thử lại";

    private final PlayerRepository repository;
    private final PlayerProfileFactory initialFactory;
    private final Clock clock;

    public PlayerService(PlayerRepository repository) {
        this(repository, new PlayerProfileFactory(), Clock.systemUTC());
    }

    public PlayerService(PlayerRepository repository, PlayerProfileFactory initialFactory,
                          Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.initialFactory = Objects.requireNonNull(initialFactory, "initialFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlayerLoadResult load(long accountId) {
        if (accountId <= 0L) {
            return PlayerLoadResult.failure("Tài khoản không hợp lệ");
        }
        try {
            Optional<PlayerRecord> record = repository.findByAccountId(accountId);
            return record.isPresent()
                    ? PlayerLoadResult.found(record.orElseThrow().toProfile(0))
                    : PlayerLoadResult.missing();
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER load repository failure accountId=" + accountId, exception);
            return PlayerLoadResult.failure(SYSTEM_BUSY);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "PLAYER load invalid persisted data accountId=" + accountId,
                    exception);
            return PlayerLoadResult.failure(SYSTEM_BUSY);
        }
    }

    public PlayerResult create(long accountId, String name, int gender) {
        final PlayerProfile initial;
        try {
            initial = initialFactory.create(accountId, name, gender);
        } catch (RuntimeException exception) {
            return PlayerResult.failure("Thông tin nhân vật không hợp lệ");
        }
        try {
            PlayerRecord created = repository.create(PlayerRecord.withoutId(initial));
            return PlayerResult.success(created.toProfile(0));
        } catch (DuplicatePlayerException exception) {
            return PlayerResult.failure("Nhân vật đã tồn tại");
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER create repository failure accountId=" + accountId, exception);
            return PlayerResult.failure(SYSTEM_BUSY);
        }
    }

    public boolean checkpoint(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        try {
            repository.updateCheckpoint(profile, clock.instant());
            return true;
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER checkpoint repository failure playerId=" + profile.id(), exception);
            return false;
        }
    }

    public record PlayerLoadResult(boolean success, boolean found, PlayerProfile player, String message) {
        static PlayerLoadResult found(PlayerProfile player) {
            return new PlayerLoadResult(true, true, player, "");
        }

        static PlayerLoadResult missing() {
            return new PlayerLoadResult(true, false, null, "");
        }

        static PlayerLoadResult failure(String message) {
            return new PlayerLoadResult(false, false, null, message);
        }
    }

    public record PlayerResult(boolean success, PlayerProfile player, String message) {
        static PlayerResult success(PlayerProfile player) {
            return new PlayerResult(true, player, "Tạo nhân vật thành công");
        }

        static PlayerResult failure(String message) {
            return new PlayerResult(false, null, message);
        }
    }
}
