package com.project.game.persistence.account;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcAccountRepository implements AccountRepository {
    private static final String FIND_BY_USERNAME_SQL = """
            SELECT
                id,
                username,
                password_hash,
                password_salt,
                role,
                is_locked,
                ip_address,
                created_at,
                updated_at,
                last_login_at
            FROM account
            WHERE username = ?
            LIMIT 1
            """;
    private static final String CREATE_SQL = """
            INSERT INTO account (
                username,
                password_hash,
                password_salt,
                ip_address
            )
            VALUES (?, ?, ?, ?)
            """;
    private static final String UPDATE_SUCCESSFUL_LOGIN_SQL = """
            UPDATE account
            SET
                ip_address = ?,
                last_login_at = ?
            WHERE id = ?
            """;

    private final DataSource dataSource;

    public JdbcAccountRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<AccountRecord> findByUsername(String username) {
        requireUsername(username);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(mapRecord(results))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new AccountRepositoryException("failed to find account by username", exception);
        }
    }

    @Override
    public long create(String username, byte[] passwordHash, byte[] passwordSalt, String ipAddress) {
        requireUsername(username);
        requireBytes(passwordHash, 32, "passwordHash");
        requireBytes(passwordSalt, 16, "passwordSalt");
        requireIpAddress(ipAddress);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     CREATE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setBytes(2, passwordHash);
            statement.setBytes(3, passwordSalt);
            statement.setString(4, ipAddress);
            if (statement.executeUpdate() != 1) {
                throw new AccountRepositoryException("account insert did not affect one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new AccountRepositoryException("account insert returned no generated key");
                }
                long id = keys.getLong(1);
                if (id <= 0) {
                    throw new AccountRepositoryException("account insert returned invalid generated key");
                }
                return id;
            }
        } catch (SQLException exception) {
            if (isDuplicateUsername(exception)) {
                throw new DuplicateAccountException("account username already exists", exception);
            }
            throw new AccountRepositoryException("failed to create account", exception);
        }
    }

    @Override
    public void updateSuccessfulLogin(long accountId, String ipAddress, Instant loginAt) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        requireIpAddress(ipAddress);
        Objects.requireNonNull(loginAt, "loginAt");
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SUCCESSFUL_LOGIN_SQL)) {
            statement.setString(1, ipAddress);
            statement.setTimestamp(2, Timestamp.from(loginAt));
            statement.setLong(3, accountId);
            if (statement.executeUpdate() != 1) {
                throw new AccountRepositoryException(
                        "successful login update did not affect one row");
            }
        } catch (SQLException exception) {
            throw new AccountRepositoryException("failed to update successful login", exception);
        }
    }

    private static AccountRecord mapRecord(ResultSet results) throws SQLException {
        return new AccountRecord(
                results.getLong("id"),
                results.getString("username"),
                results.getBytes("password_hash"),
                results.getBytes("password_salt"),
                results.getInt("role"),
                results.getBoolean("is_locked"),
                results.getString("ip_address"),
                instant(results.getTimestamp("created_at")),
                instant(results.getTimestamp("updated_at")),
                instant(results.getTimestamp("last_login_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    private static void requireBytes(byte[] bytes, int length, String name) {
        if (bytes == null || bytes.length != length) {
            throw new IllegalArgumentException(name + " must contain exactly " + length + " bytes");
        }
    }

    private static void requireIpAddress(String ipAddress) {
        if (ipAddress != null && ipAddress.length() > 45) {
            throw new IllegalArgumentException("ipAddress is too long");
        }
    }

    private static boolean isDuplicateUsername(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1062) {
                return true;
            }
            String message = current.getMessage();
            if ("23000".equals(current.getSQLState())
                    && message != null
                    && message.toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_account_username")) {
                return true;
            }
        }
        return false;
    }
}
