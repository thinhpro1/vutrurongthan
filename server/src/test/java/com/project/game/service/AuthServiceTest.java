package com.project.game.service;

import com.project.game.persistence.account.AccountRecord;
import com.project.game.player.PlayerProfile;
import com.project.game.testsupport.TestAccountRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    @Test
    void registerPersistsNormalizedCredentialAndRegistrationIp() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);

        AuthService.AuthResult result = auth.register("USER01", "secret1", "192.0.2.10");

        assertTrue(result.success());
        assertEquals("Đăng ký thành công", result.value());
        AccountRecord account = repository.requireAccount("user01");
        assertEquals("user01", account.username());
        assertEquals(32, account.passwordHash().length);
        assertEquals(16, account.passwordSalt().length);
        assertEquals("192.0.2.10", account.ipAddress());
    }

    @Test
    void duplicateRegisterUsesExistingVietnameseMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AuthService.AuthResult duplicate = auth.register("USER01", "secret2", "192.0.2.11");

        assertFalse(duplicate.success());
        assertEquals("Tài khoản đã tồn tại", duplicate.value());
        assertEquals(1, repository.accountCount());
    }

    @Test
    void invalidRegisterCreatesNoAccount() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);

        AuthService.AuthResult result = auth.register("bad", "secret1", "192.0.2.10");

        assertFalse(result.success());
        assertEquals("Tài khoản hoặc mật khẩu không hợp lệ", result.value());
        assertEquals(0, repository.accountCount());
    }

    @Test
    void repositoryRegisterFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        repository.failCreate(true);
        AuthService auth = new AuthService(repository);

        AuthService.AuthResult result = auth.register("user01", "secret1", "192.0.2.10");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.value());
    }

    @Test
    void loginReturnsPersistentIdentityForCorrectNormalizedCredential() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AuthService.LoginResult result = auth.login("USER01", "secret1");

        assertTrue(result.success());
        assertTrue(result.accountId() > 0);
        assertEquals("user01", result.accountName());
    }

    @Test
    void wrongPasswordAndMissingAccountUseSameGenericMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AuthService.LoginResult wrongPassword = auth.login("user01", "secret2");
        AuthService.LoginResult missing = auth.login("user02", "secret2");

        assertFalse(wrongPassword.success());
        assertFalse(missing.success());
        assertEquals("Tài khoản hoặc mật khẩu không chính xác", wrongPassword.message());
        assertEquals(wrongPassword.message(), missing.message());
    }

    @Test
    void lockedAccountCannotLoginOrReceiveMetadataUpdate() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        repository.lock("user01");

        AuthService.LoginResult result = auth.login("user01", "secret1");

        assertFalse(result.success());
        assertEquals("Tài khoản đã bị khóa", result.message());
        assertEquals(0, repository.metadataUpdateCount());
    }

    @Test
    void repositoryLoginFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        repository.failFind(true);
        AuthService auth = new AuthService(repository);

        AuthService.LoginResult result = auth.login("user01", "secret1");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.message());
    }

    @Test
    void markSuccessfulLoginUpdatesIpAndTimestamp() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        long accountId = auth.login("user01", "secret1").accountId();

        AuthService.AuthResult result = auth.markSuccessfulLogin(accountId, "198.51.100.20");

        assertTrue(result.success());
        AccountRecord account = repository.requireAccount("user01");
        assertEquals("198.51.100.20", account.ipAddress());
        assertNotNull(account.lastLoginAt());
        assertEquals(1, repository.metadataUpdateCount());
    }

    @Test
    void metadataRepositoryFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        AuthService auth = new AuthService(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        long accountId = auth.login("user01", "secret1").accountId();
        repository.failUpdate(true);

        AuthService.AuthResult result = auth.markSuccessfulLogin(accountId, "198.51.100.20");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.value());
    }

    @Test
    void createPlayerAssignsPositiveIncreasingIds() {
        AuthService auth = registeredAuthWithTwoUsers();

        PlayerProfile first = auth.createPlayer("user01", "alpha1", 0).player();
        PlayerProfile second = auth.createPlayer("user02", "beta22", 1).player();

        assertEquals(1, first.id());
        assertEquals(2, second.id());
    }

    @Test
    void freshPlayerUsesLegacyInitialScalarSnapshot() {
        AuthService auth = registeredAuth();
        PlayerProfile player = auth.createPlayer("user01", "alpha1", 0).player();

        assertEquals(1, player.power());
        assertEquals(1, player.potential());
        assertEquals(1, player.level());
        assertEquals(1, player.pointSkill());

        assertEquals(10, player.baseDamage());
        assertEquals(5, player.baseHp());
        assertEquals(5, player.baseMp());
        assertEquals(5, player.baseConstitution());

        assertEquals(10, player.potentialUpDamage());
        assertEquals(10, player.potentialUpHp());
        assertEquals(10, player.potentialUpMp());
        assertEquals(10, player.potentialUpConstitution());

        assertEquals(150, player.maxHp());
        assertEquals(150, player.maxMp());
        assertEquals(100, player.hp());
        assertEquals(100, player.mp());

        assertEquals(12, player.speed());
        assertEquals(0, player.pointPk());
        assertEquals(0, player.pointActivity());
        assertEquals(1, player.countBarrack());

        assertEquals("0%", player.dodge());
        assertEquals("0%", player.critical());
        assertEquals("0%", player.reduceDamage());
        assertEquals("0%", player.bloodsucking());
        assertEquals("0%", player.manaSucking());
        assertEquals("0%", player.strikeBack());

        assertEquals(10, player.damage());
        assertEquals(0, player.coin());
        assertEquals(10_000, player.coinLock());
        assertEquals(0, player.diamond());
        assertEquals(25, player.ruby());
        assertEquals(0, player.spaceship());
    }

    @Test
    void freshPlayerUsesLegacyGenderParts() {
        AuthService auth = registeredAuthWithTwoUsers();
        PlayerProfile gender0 = auth.createPlayer("user01", "alpha1", 0).player();
        PlayerProfile gender1 = auth.createPlayer("user02", "beta22", 1).player();

        AuthService thirdAuth = registeredAuthFor("user03");
        PlayerProfile gender2 = thirdAuth.createPlayer("user03", "gamma3", 2).player();

        assertEquals(5, gender0.head());
        assertEquals(6, gender0.body());
        assertEquals(3, gender1.head());
        assertEquals(7, gender1.body());
        assertEquals(4, gender2.head());
        assertEquals(8, gender2.body());
        for (PlayerProfile player : new PlayerProfile[]{gender0, gender1, gender2}) {
            assertEquals(-1, player.mount());
            assertEquals(-1, player.bag());
            assertEquals(-1, player.medal());
            assertEquals(-1, player.aura());
        }
    }

    private static AuthService registeredAuth() {
        return registeredAuthFor("user01");
    }

    private static AuthService registeredAuthWithTwoUsers() {
        AuthService auth = registeredAuthFor("user01");
        assertTrue(auth.register("user02", "secret2", "127.0.0.1").success());
        return auth;
    }

    private static AuthService registeredAuthFor(String username) {
        AuthService auth = new AuthService(new TestAccountRepository());
        assertTrue(auth.register(username, "secret1", "127.0.0.1").success());
        return auth;
    }
}
