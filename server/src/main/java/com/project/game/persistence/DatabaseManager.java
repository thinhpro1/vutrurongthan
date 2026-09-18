package com.project.game.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Function;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.username().isBlank()) {
            throw new IllegalStateException("database username is not configured");
        }
        String password = resolvePassword(config, System::getenv);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setMinimumIdle(config.minimumIdle());
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setPoolName("rongthan-db");
        dataSource = new HikariDataSource(hikari);
    }

    static String resolvePassword(
            DatabaseConfig config,
            Function<String, String> environment) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(environment, "environment");
        String password = environment.apply(config.passwordEnvironmentVariable());
        if (password == null) {
            if (config.allowEmptyPassword()) {
                return "";
            }
            throw new IllegalStateException(
                    "database password environment variable is not configured: "
                            + config.passwordEnvironmentVariable());
        }
        return password;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
