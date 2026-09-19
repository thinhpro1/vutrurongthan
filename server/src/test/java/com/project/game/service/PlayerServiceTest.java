package com.project.game.service;

import com.project.game.player.PlayerInitialProfileFactory;
import com.project.game.player.PlayerProfile;
import com.project.game.testsupport.TestPlayerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerServiceTest {
    @Test
    void missingPlayerIsDistinguishedFromRepositoryFailure() {
        TestPlayerRepository repository = new TestPlayerRepository();
        PlayerService service = new PlayerService(repository);

        PlayerService.PlayerLoadResult result = service.load(101L);

        assertTrue(result.success());
        assertFalse(result.found());
        assertEquals(null, result.player());
    }

    @Test
    void existingPlayerLoadsWithRuntimeZoneZero() {
        TestPlayerRepository repository = new TestPlayerRepository();
        PlayerProfile created = new PlayerService(repository).create(101L, "alpha1", 0).player();

        PlayerService.PlayerLoadResult result = new PlayerService(repository).load(101L);

        assertTrue(result.success());
        assertTrue(result.found());
        assertEquals(created, result.player());
        assertEquals(0, result.player().zoneId());
    }

    @Test
    void createUsesRepositoryGeneratedIdAndExplicitInitialValues() {
        TestPlayerRepository repository = new TestPlayerRepository();
        PlayerService.PlayerResult result = new PlayerService(repository).create(101L, "alpha1", 0);

        assertTrue(result.success());
        assertTrue(result.player().id() > 0);
        assertEquals(200, result.player().baseStats().hp());
        assertEquals(200, result.player().currentStats().maxHp());
        assertEquals(200, result.player().hp());
        assertEquals(12, result.player().currentStats().speed());
    }

    @Test
    void duplicatePlayerAndRepositoryFailureUseSafeMessages() {
        TestPlayerRepository repository = new TestPlayerRepository();
        PlayerService service = new PlayerService(repository);
        assertTrue(service.create(101L, "alpha1", 0).success());

        PlayerService.PlayerResult duplicateAccount = service.create(101L, "beta22", 1);
        assertFalse(duplicateAccount.success());
        assertEquals("Nhân vật đã tồn tại", duplicateAccount.message());
        PlayerService.PlayerResult duplicateName = service.create(102L, "alpha1", 1);
        assertFalse(duplicateName.success());
        assertEquals("Nhân vật đã tồn tại", duplicateName.message());

        repository.failFind(true);
        PlayerService.PlayerLoadResult failure = service.load(101L);
        assertFalse(failure.success());
        assertFalse(failure.found());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", failure.message());
    }

    @Test
    void unexpectedRepositoryRuntimeFailureStillReturnsSafeLoadFailure() {
        TestPlayerRepository repository = new TestPlayerRepository();
        repository.failFindRuntime(true);

        PlayerService.PlayerLoadResult failure = new PlayerService(repository).load(101L);

        assertFalse(failure.success());
        assertFalse(failure.found());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", failure.message());
    }

    @Test
    void checkpointPersistsUpdatedRuntimeProfile() {
        TestPlayerRepository repository = new TestPlayerRepository();
        PlayerService service = new PlayerService(
                repository,
                new PlayerInitialProfileFactory(),
                Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
        PlayerProfile created = service.create(101L, "alpha1", 0).player();
        PlayerProfile changed = created.withHp(77).withPotential(99).withLocation(1, 0, 90, 1008);

        assertTrue(service.checkpoint(changed));
        assertEquals(changed, service.load(101L).player());
    }
}
