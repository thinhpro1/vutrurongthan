package com.project.game.player;

import com.project.game.persistence.player.DuplicatePlayerException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Service Player bền vững theo account; state realtime nằm trong Session/MapManager. */
public final class PlayerService {
    private static final Logger LOGGER = Logger.getLogger(PlayerService.class.getName());
    private static final String SYSTEM_BUSY = "Hệ thống đang bận, vui lòng thử lại";

    private final PlayerRepository repository;
    private final Clock clock;

    public PlayerService(PlayerRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public PlayerService(PlayerRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlayerLoadResult load(long accountId) {
        if (accountId <= 0L) {
            return PlayerLoadResult.failure("Tài khoản không hợp lệ");
        }
        try {
            Optional<PlayerRecord> record = repository.findByAccountId(accountId);
            return record.isPresent()
                    ? PlayerLoadResult.found(record.orElseThrow().toPlayer(0))
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
        final Player initial;
        try {
            initial = Player.create(accountId, name, gender);
        } catch (RuntimeException exception) {
            return PlayerResult.failure("Thông tin nhân vật không hợp lệ");
        }
        try {
            PlayerRecord created = repository.create(PlayerRecord.withoutId(initial));
            return PlayerResult.success(created.toPlayer(0));
        } catch (DuplicatePlayerException exception) {
            return PlayerResult.failure("Nhân vật đã tồn tại");
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER create repository failure accountId=" + accountId, exception);
            return PlayerResult.failure(SYSTEM_BUSY);
        }
    }

    public boolean checkpoint(PlayerSaveData saveData) {
        Objects.requireNonNull(saveData, "saveData");
        try {
            repository.updateCheckpoint(saveData, clock.instant());
            return true;
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING, "PLAYER checkpoint repository failure playerId=" + saveData.id(), exception);
            return false;
        }
    }

    public record PlayerLoadResult(boolean success, boolean found, Player player, String message) {
        static PlayerLoadResult found(Player player) {
            return new PlayerLoadResult(true, true, player, "");
        }

        static PlayerLoadResult missing() {
            return new PlayerLoadResult(true, false, null, "");
        }

        static PlayerLoadResult failure(String message) {
            return new PlayerLoadResult(false, false, null, message);
        }
    }

    public record PlayerResult(boolean success, Player player, String message) {
        static PlayerResult success(Player player) {
            return new PlayerResult(true, player, "Tạo nhân vật thành công");
        }

        static PlayerResult failure(String message) {
            return new PlayerResult(false, null, message);
        }
    }
}
