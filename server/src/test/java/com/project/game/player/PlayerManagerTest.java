package com.project.game.player;

import com.project.game.persistence.player.DuplicatePlayerException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerManagerTest {
    @Test
    void loadConvertsPersistedPlayerToRuntimeZoneZero() {
        RecordingRepository repository = new RecordingRepository();
        Player persisted = Player.createWithId(17, 101L, "alpha1", 0);
        persisted.changeMap(2, 7, 333, 444);
        repository.record = PlayerRecord.fromSaveData(PlayerSaveData.capture(persisted));

        Player loaded = new PlayerManager(repository).load(101L);

        assertNotSame(persisted, loaded);
        assertEquals(17, loaded.id());
        assertEquals(101L, loaded.accountId());
        assertEquals(0, loaded.zoneId());
        assertEquals(2, loaded.mapId());
        assertEquals(333, loaded.x());
        assertEquals(444, loaded.y());
    }

    @Test
    void loadReturnsNullWhenAccountHasNoPlayer() {
        Player loaded = new PlayerManager(new RecordingRepository()).load(101L);

        assertNull(loaded);
    }

    @Test
    void createUsesRepositoryIdAndPreservesPlayerDefaults() {
        RecordingRepository repository = new RecordingRepository();
        repository.created = withId(Player.create(101L, "alpha1", 0), 42);

        Player created = new PlayerManager(repository).create(101L, "Alpha1", 0);

        assertEquals(42, created.id());
        assertEquals(101L, created.accountId());
        assertEquals("alpha1", created.name());
        assertEquals(0, created.zoneId());
        assertEquals(0, created.mapId());
        assertEquals(1250, created.x());
        assertEquals(648, created.y());
        assertEquals(200, created.hp());
        assertEquals(1, repository.createCalls);
    }

    @Test
    void createPropagatesDuplicatePlayerException() {
        RecordingRepository repository = new RecordingRepository();
        repository.createFailure = new DuplicatePlayerException("duplicate", null);

        assertThrows(DuplicatePlayerException.class,
                () -> new PlayerManager(repository).create(101L, "alpha1", 0));
    }

    @Test
    void createRejectsInvalidPlayerBeforeRepositoryCreate() {
        RecordingRepository repository = new RecordingRepository();

        assertThrows(IllegalArgumentException.class,
                () -> new PlayerManager(repository).create(101L, "bad", 0));

        assertEquals(0, repository.createCalls);
    }

    @Test
    void saveDelegatesStableDataExactlyOnce() {
        RecordingRepository repository = new RecordingRepository();
        PlayerSaveData player = PlayerSaveData.capture(Player.createWithId(9, 101L, "alpha1", 0));

        new PlayerManager(repository).save(player);

        assertEquals(1, repository.saveCalls);
        assertSame(player, repository.saved);
    }

    @Test
    void loadWrapsPersistedRuntimeValidationFailure() {
        RecordingRepository repository = new RecordingRepository();
        Player valid = Player.createWithId(17, 101L, "alpha1", 0);
        PlayerRecord record = PlayerRecord.fromSaveData(PlayerSaveData.capture(valid));
        repository.record = new PlayerRecord(
                record.id(), record.accountId(), record.name(), 99, record.power(),
                record.potential(), record.level(), record.exp(), record.baseStats(),
                record.currentStats(), record.hp(), record.mp(), record.appearance(),
                record.coin(), record.coinLock(), record.diamond(), record.ruby(),
                record.mapId(), record.x(), record.y());

        PlayerRepositoryException failure = assertThrows(PlayerRepositoryException.class,
                () -> new PlayerManager(repository).load(101L));

        assertEquals("failed to load player runtime", failure.getMessage());
        assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
    }

    private static PlayerRecord withId(Player player, int id) {
        PlayerSaveData saved = PlayerSaveData.capture(player);
        return new PlayerRecord(
                id, saved.accountId(), saved.name(), saved.gender(), saved.power(),
                saved.potential(), saved.level(), saved.exp(), saved.baseStats(),
                saved.currentStats(), saved.hp(), saved.mp(), saved.appearance(),
                saved.coin(), saved.coinLock(), saved.diamond(), saved.ruby(),
                saved.mapId(), saved.x(), saved.y());
    }

    private static final class RecordingRepository implements PlayerRepository {
        private PlayerRecord record;
        private PlayerRecord created;
        private RuntimeException createFailure;
        private int createCalls;
        private int saveCalls;
        private PlayerSaveData saved;

        @Override
        public Optional<PlayerRecord> findByAccountId(long accountId) {
            return Optional.ofNullable(record);
        }

        @Override
        public PlayerRecord create(PlayerRecord initialWithoutId) {
            createCalls++;
            if (createFailure != null) {
                throw createFailure;
            }
            if (created == null) {
                throw new IllegalStateException("test repository result not configured");
            }
            return created;
        }

        @Override
        public void save(PlayerSaveData player) {
            saveCalls++;
            saved = player;
        }
    }
}
