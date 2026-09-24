package com.project.game.account;

import com.project.game.persistence.account.AccountRecord;
import com.project.game.testsupport.TestAccountRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountAuthTest {
    @Test
    void registerPersistsNormalizedCredentialAndRegistrationIp() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);

        AccountAuth.AuthResult result = auth.register("USER01", "secret1", "192.0.2.10");

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
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AccountAuth.AuthResult duplicate = auth.register("USER01", "secret2", "192.0.2.11");

        assertFalse(duplicate.success());
        assertEquals("Tài khoản đã tồn tại", duplicate.value());
        assertEquals(1, repository.accountCount());
    }

    @Test
    void invalidRegisterCreatesNoAccount() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);

        AccountAuth.AuthResult result = auth.register("bad", "secret1", "192.0.2.10");

        assertFalse(result.success());
        assertEquals("Tài khoản hoặc mật khẩu không hợp lệ", result.value());
        assertEquals(0, repository.accountCount());
    }

    @Test
    void repositoryRegisterFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        repository.failCreate(true);
        AccountAuth auth = new AccountAuth(repository);

        AccountAuth.AuthResult result = auth.register("user01", "secret1", "192.0.2.10");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.value());
    }

    @Test
    void loginReturnsPersistentIdentityForCorrectNormalizedCredential() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AccountAuth.LoginResult result = auth.login("USER01", "secret1");

        assertTrue(result.success());
        assertTrue(result.accountId() > 0);
        assertEquals("user01", result.accountName());
    }

    @Test
    void wrongPasswordAndMissingAccountUseSameGenericMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());

        AccountAuth.LoginResult wrongPassword = auth.login("user01", "secret2");
        AccountAuth.LoginResult missing = auth.login("user02", "secret2");

        assertFalse(wrongPassword.success());
        assertFalse(missing.success());
        assertEquals("Tài khoản hoặc mật khẩu không chính xác", wrongPassword.message());
        assertEquals(wrongPassword.message(), missing.message());
    }

    @Test
    void lockedAccountCannotLoginOrReceiveMetadataUpdate() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        repository.lock("user01");

        AccountAuth.LoginResult result = auth.login("user01", "secret1");

        assertFalse(result.success());
        assertEquals("Tài khoản đã bị khóa", result.message());
        assertEquals(0, repository.metadataUpdateCount());
    }

    @Test
    void repositoryLoginFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        repository.failFind(true);
        AccountAuth auth = new AccountAuth(repository);

        AccountAuth.LoginResult result = auth.login("user01", "secret1");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.message());
    }

    @Test
    void markSuccessfulLoginUpdatesIpAndTimestamp() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        long accountId = auth.login("user01", "secret1").accountId();

        AccountAuth.AuthResult result = auth.markSuccessfulLogin(accountId, "198.51.100.20");

        assertTrue(result.success());
        AccountRecord account = repository.requireAccount("user01");
        assertEquals("198.51.100.20", account.ipAddress());
        assertNotNull(account.lastLoginAt());
        assertEquals(1, repository.metadataUpdateCount());
    }

    @Test
    void metadataRepositoryFailureReturnsSafeMessage() {
        TestAccountRepository repository = new TestAccountRepository();
        AccountAuth auth = new AccountAuth(repository);
        assertTrue(auth.register("user01", "secret1", "192.0.2.10").success());
        long accountId = auth.login("user01", "secret1").accountId();
        repository.failUpdate(true);

        AccountAuth.AuthResult result = auth.markSuccessfulLogin(accountId, "198.51.100.20");

        assertFalse(result.success());
        assertEquals("Hệ thống đang bận, vui lòng thử lại", result.value());
    }

    private static AccountAuth registeredAuth() {
        return registeredAuthFor("user01");
    }

    private static AccountAuth registeredAuthFor(String username) {
        AccountAuth auth = new AccountAuth(new TestAccountRepository());
        assertTrue(auth.register(username, "secret1", "127.0.0.1").success());
        return auth;
    }
}
