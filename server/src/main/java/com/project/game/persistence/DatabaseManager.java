package com.project.game.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Objects;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        String password = System.getenv(config.passwordEnvironmentVariable());
        if (password == null) {
            throw new IllegalStateException(
                    "database password environment variable is not configured: "
                            + config.passwordEnvironmentVariable());
        }

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

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
