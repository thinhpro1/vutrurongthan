package com.project.game.service;

import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.AccountRepository;
import com.project.game.persistence.account.AccountRepositoryException;
import com.project.game.persistence.account.DuplicateAccountException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Account authentication boundary backed by the configured account repository. Player state
 * is deliberately limited to account authentication and metadata.
 */
public final class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final String SYSTEM_BUSY = "Hệ thống đang bận, vui lòng thử lại";
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9]{5,25}$");
    private static final Pattern PASSWORD = Pattern.compile("^[a-z0-9]{5,25}$");
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accountRepository;

    public AuthService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    }

    public AuthResult register(String username, String password, String ipAddress) {
        String normalized = normalize(username);
        if (!USERNAME.matcher(normalized).matches()
                || password == null
                || !PASSWORD.matcher(password).matches()) {
            return AuthResult.failure("Tài khoản hoặc mật khẩu không hợp lệ");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        try {
            accountRepository.create(normalized, hash(password, salt), salt, ipAddress);
            return AuthResult.success("Đăng ký thành công");
        } catch (DuplicateAccountException exception) {
            return AuthResult.failure("Tài khoản đã tồn tại");
        } catch (AccountRepositoryException exception) {
            LOGGER.log(Level.WARNING, "REGISTER repository failure username=" + normalized, exception);
            return AuthResult.failure(SYSTEM_BUSY);
        }
    }

    public LoginResult login(String username, String password) {
        String normalized = normalize(username);
        if (!USERNAME.matcher(normalized).matches()
                || password == null
                || !PASSWORD.matcher(password).matches()) {
            return LoginResult.failure("Tài khoản hoặc mật khẩu không chính xác");
        }
        try {
            AccountRecord account = accountRepository.findByUsername(normalized).orElse(null);
            if (account == null) {
                return LoginResult.failure("Tài khoản hoặc mật khẩu không chính xác");
            }
            if (account.locked()) {
                return LoginResult.failure("Tài khoản đã bị khóa");
            }
            byte[] candidate = hash(password, account.passwordSalt());
            if (!MessageDigest.isEqual(account.passwordHash(), candidate)) {
                return LoginResult.failure("Tài khoản hoặc mật khẩu không chính xác");
            }
            return LoginResult.success(account.id(), normalize(account.username()));
        } catch (AccountRepositoryException exception) {
            LOGGER.log(Level.WARNING, "LOGIN repository failure username=" + normalized, exception);
            return LoginResult.failure(SYSTEM_BUSY);
        }
    }

    public AuthResult markSuccessfulLogin(long accountId, String ipAddress) {
        try {
            accountRepository.updateSuccessfulLogin(accountId, ipAddress, Instant.now());
            return AuthResult.success("");
        } catch (AccountRepositoryException exception) {
            LOGGER.log(Level.WARNING,
                    "LOGIN metadata repository failure accountId=" + accountId, exception);
            return AuthResult.failure(SYSTEM_BUSY);
        }
    }

    private byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 is unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    public record AuthResult(boolean success, String value) {
        static AuthResult success(String value) {
            return new AuthResult(true, value);
        }

        static AuthResult failure(String value) {
            return new AuthResult(false, value);
        }
    }

    public record LoginResult(boolean success, long accountId, String accountName, String message) {
        static LoginResult success(long accountId, String accountName) {
            return new LoginResult(true, accountId, accountName, "");
        }

        static LoginResult failure(String message) {
            return new LoginResult(false, 0, null, message);
        }
    }

}
