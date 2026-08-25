package com.project.game.service;

import com.project.game.player.PlayerProfile;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * N11 development auth boundary. The storage is intentionally in-memory so the new project
 * can prove the protocol flow before a repository/DB gate is introduced.
 */
public final class AuthService {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9]{5,25}$");
    private static final Pattern PASSWORD = Pattern.compile("^[a-z0-9]{5,25}$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^[a-z0-9]{5,10}$");
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Credential> credentials = new ConcurrentHashMap<>();
    private final Map<String, PlayerProfile> players = new ConcurrentHashMap<>();

    public AuthResult register(String username, String password) {
        String normalized = normalize(username);
        if (!USERNAME.matcher(normalized).matches() || !PASSWORD.matcher(password).matches()) {
            return AuthResult.failure("Tài khoản hoặc mật khẩu không hợp lệ");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Credential credential = new Credential(salt, hash(password, salt));
        return credentials.putIfAbsent(normalized, credential) == null
                ? AuthResult.success("Đăng ký thành công")
                : AuthResult.failure("Tài khoản đã tồn tại");
    }

    public AuthResult login(String username, String password) {
        String normalized = normalize(username);
        Credential credential = credentials.get(normalized);
        if (credential == null || !Arrays.equals(credential.hash(), hash(password, credential.salt()))) {
            return AuthResult.failure("Tài khoản hoặc mật khẩu không chính xác");
        }
        return AuthResult.success(normalized);
    }

    public PlayerResult createPlayer(String accountName, String name, int gender) {
        String normalized = normalize(name);
        if (!PLAYER_NAME.matcher(normalized).matches() || gender < 0 || gender > 2) {
            return PlayerResult.failure("Thông tin nhân vật không hợp lệ");
        }
        PlayerProfile profile = new PlayerProfile(accountName, normalized, gender);
        return players.values().stream().anyMatch(player -> player.name().equals(normalized))
                || players.putIfAbsent(accountName, profile) != null
                ? PlayerResult.failure("Nhân vật đã tồn tại")
                : PlayerResult.success(profile);
    }

    public PlayerProfile findPlayer(String accountName) {
        return players.get(accountName);
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

    private record Credential(byte[] salt, byte[] hash) {
        private Credential {
            salt = salt.clone();
            hash = hash.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }

    public record AuthResult(boolean success, String value) {
        static AuthResult success(String value) {
            return new AuthResult(true, value);
        }

        static AuthResult failure(String value) {
            return new AuthResult(false, value);
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
