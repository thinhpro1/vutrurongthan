package com.project.game.persistence.player;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.project.game.player.Appearance;
import com.project.game.player.BaseStats;
import com.project.game.player.CurrentStats;
import com.project.game.player.PlayerSaveData;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcPlayerRepository implements PlayerRepository {
    private static final String PROBE_TABLE_SQL = "SELECT 1 FROM player LIMIT 1";
    private static final String FIND_BY_ACCOUNT_SQL = """
            SELECT id, account_id, name, gender, power, potential, level, exp,
                   base_stats, current_stats, hp, mp, appearance,
                   coin, coin_lock, diamond, ruby, position
            FROM player
            WHERE account_id = ?
            LIMIT 1
            """;
    private static final String CREATE_SQL = """
            INSERT INTO player (
                account_id, name, gender, power, potential, level, exp,
                base_stats, current_stats, hp, mp, appearance,
                coin, coin_lock, diamond, ruby, position
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_SQL = """
            UPDATE player
            SET power = ?, potential = ?, level = ?, exp = ?,
                base_stats = ?, current_stats = ?, hp = ?, mp = ?, appearance = ?,
                coin = ?, coin_lock = ?, diamond = ?, ruby = ?, position = ?,
                last_played_at = ?
            WHERE id = ? AND account_id = ?
            """;

    private static final Gson GSON = new Gson();
    private final DataSource dataSource;

    public JdbcPlayerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Verifies table availability without reading or deserializing any player row. */
    public void probeTable() {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PROBE_TABLE_SQL);
             ResultSet ignored = statement.executeQuery()) {
            // The query itself is the health check. Existing rows are intentionally not mapped.
        } catch (SQLException exception) {
            throw new PlayerRepositoryException("failed to probe player table", exception);
        }
    }

    @Override
    public Optional<PlayerRecord> findByAccountId(long accountId) {
        requireAccountId(accountId);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ACCOUNT_SQL)) {
            statement.setLong(1, accountId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapRecord(results)) : Optional.empty();
            }
        } catch (PlayerRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PlayerRepositoryException("failed to find player by account", exception);
        } catch (RuntimeException exception) {
            throw new PlayerRepositoryException("failed to parse player row", exception);
        }
    }

    @Override
    public PlayerRecord create(PlayerRecord initialWithoutId) {
        Objects.requireNonNull(initialWithoutId, "initialWithoutId");
        if (initialWithoutId.id() != 0) {
            throw new IllegalArgumentException("new player record must not have an id");
        }
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     CREATE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindCreate(statement, initialWithoutId);
            if (statement.executeUpdate() != 1) {
                throw new PlayerRepositoryException("player insert did not affect one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new PlayerRepositoryException("player insert returned no generated key");
                }
                int id = keys.getInt(1);
                if (id <= 0) {
                    throw new PlayerRepositoryException("player insert returned invalid generated key");
                }
                return new PlayerRecord(
                        id, initialWithoutId.accountId(), initialWithoutId.name(),
                        initialWithoutId.gender(), initialWithoutId.power(),
                        initialWithoutId.potential(), initialWithoutId.level(),
                        initialWithoutId.exp(), initialWithoutId.baseStats(),
                        initialWithoutId.currentStats(), initialWithoutId.hp(),
                        initialWithoutId.mp(), initialWithoutId.appearance(),
                        initialWithoutId.coin(), initialWithoutId.coinLock(),
                        initialWithoutId.diamond(), initialWithoutId.ruby(),
                        initialWithoutId.mapId(), initialWithoutId.x(), initialWithoutId.y());
            }
        } catch (SQLException exception) {
            if (isDuplicate(exception)) {
                throw new DuplicatePlayerException("player name or account already exists", exception);
            }
            throw new PlayerRepositoryException("failed to create player", exception);
        }
    }

    @Override
    public void updateCheckpoint(PlayerSaveData player, Instant playedAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playedAt, "playedAt");
        if (player.id() <= 0) {
            throw new IllegalArgumentException("persisted player id must be positive");
        }
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            int index = 1;
            statement.setLong(index++, player.power());
            statement.setLong(index++, player.potential());
            statement.setInt(index++, player.level());
            statement.setLong(index++, player.exp());
            statement.setString(index++, GSON.toJson(player.baseStats()));
            statement.setString(index++, GSON.toJson(player.currentStats()));
            statement.setInt(index++, player.hp());
            statement.setInt(index++, player.mp());
            statement.setString(index++, GSON.toJson(player.appearance()));
            statement.setLong(index++, player.coin());
            statement.setLong(index++, player.coinLock());
            statement.setInt(index++, player.diamond());
            statement.setInt(index++, player.ruby());
            statement.setString(index++, positionJson(player));
            statement.setTimestamp(index++, Timestamp.from(playedAt));
            statement.setInt(index++, player.id());
            statement.setLong(index, player.accountId());
            if (statement.executeUpdate() != 1) {
                throw new PlayerRepositoryException("player checkpoint did not affect one row");
            }
        } catch (SQLException exception) {
            throw new PlayerRepositoryException("failed to update player checkpoint", exception);
        }
    }

    private static PlayerRecord mapRecord(ResultSet results) throws SQLException {
        JsonObject position = parseObject(results.getString("position"), "position", "mapId", "x", "y");
        int mapId = number(position, "mapId").intValueExact();
        int id = results.getInt("id");
        if (id <= 0) {
            throw new PlayerRepositoryException("persisted player id must be positive");
        }
        return new PlayerRecord(
                id,
                results.getLong("account_id"),
                results.getString("name"),
                results.getInt("gender"),
                results.getLong("power"),
                results.getLong("potential"),
                results.getInt("level"),
                results.getLong("exp"),
                baseStats(results.getString("base_stats")),
                currentStats(results.getString("current_stats")),
                results.getInt("hp"),
                results.getInt("mp"),
                appearance(results.getString("appearance")),
                results.getLong("coin"),
                results.getLong("coin_lock"),
                results.getInt("diamond"),
                results.getInt("ruby"),
                mapId,
                number(position, "x").intValueExact(),
                number(position, "y").intValueExact());
    }

    private static void bindCreate(PreparedStatement statement, PlayerRecord player) throws SQLException {
        int index = 1;
        statement.setLong(index++, player.accountId());
        statement.setString(index++, player.name());
        statement.setInt(index++, player.gender());
        statement.setLong(index++, player.power());
        statement.setLong(index++, player.potential());
        statement.setInt(index++, player.level());
        statement.setLong(index++, player.exp());
        statement.setString(index++, GSON.toJson(player.baseStats()));
        statement.setString(index++, GSON.toJson(player.currentStats()));
        statement.setInt(index++, player.hp());
        statement.setInt(index++, player.mp());
        statement.setString(index++, GSON.toJson(player.appearance()));
        statement.setLong(index++, player.coin());
        statement.setLong(index++, player.coinLock());
        statement.setInt(index++, player.diamond());
        statement.setInt(index++, player.ruby());
        statement.setString(index, positionJson(player));
    }

    private static String positionJson(PlayerRecord player) {
        JsonObject position = new JsonObject();
        position.addProperty("mapId", player.mapId());
        position.addProperty("x", player.x());
        position.addProperty("y", player.y());
        return GSON.toJson(position);
    }

    private static String positionJson(PlayerSaveData player) {
        JsonObject position = new JsonObject();
        position.addProperty("mapId", player.mapId());
        position.addProperty("x", player.x());
        position.addProperty("y", player.y());
        return GSON.toJson(position);
    }

    private static BaseStats baseStats(String json) {
        JsonObject object = parseObject(json, "base_stats",
                "hp", "mp", "damage", "armor", "critical", "dodge", "constitution", "speed");
        return new BaseStats(
                number(object, "hp").intValueExact(),
                number(object, "mp").intValueExact(),
                number(object, "damage").intValueExact(),
                number(object, "armor").intValueExact(),
                number(object, "critical").intValueExact(),
                number(object, "dodge").intValueExact(),
                number(object, "constitution").intValueExact(),
                number(object, "speed").intValueExact());
    }

    private static CurrentStats currentStats(String json) {
        JsonObject object = parseObject(json, "current_stats",
                "maxHp", "maxMp", "damage", "armor", "critical", "dodge", "constitution", "speed");
        return new CurrentStats(
                number(object, "maxHp").intValueExact(),
                number(object, "maxMp").intValueExact(),
                number(object, "damage").intValueExact(),
                number(object, "armor").intValueExact(),
                number(object, "critical").intValueExact(),
                number(object, "dodge").intValueExact(),
                number(object, "constitution").intValueExact(),
                number(object, "speed").intValueExact());
    }

    private static Appearance appearance(String json) {
        JsonObject object = parseObject(json, "appearance",
                "head", "body", "mount", "bag", "medal", "aura", "spaceship");
        return new Appearance(
                number(object, "head").intValueExact(),
                number(object, "body").intValueExact(),
                number(object, "mount").intValueExact(),
                number(object, "bag").intValueExact(),
                number(object, "medal").intValueExact(),
                number(object, "aura").intValueExact(),
                number(object, "spaceship").intValueExact());
    }

    private static JsonObject parseObject(String json, String label, String... required) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PlayerRepositoryException(label + " must be a JSON object");
            }
            JsonObject object = parsed.getAsJsonObject();
            for (String field : required) {
                if (!object.has(field) || !object.get(field).isJsonPrimitive()
                        || !object.get(field).getAsJsonPrimitive().isNumber()) {
                    throw new PlayerRepositoryException(label + " missing numeric field " + field);
                }
            }
            return object;
        } catch (PlayerRepositoryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlayerRepositoryException("malformed " + label + " JSON", exception);
        }
    }

    private static java.math.BigDecimal number(JsonObject object, String field) {
        try {
            return object.get(field).getAsBigDecimal();
        } catch (RuntimeException exception) {
            throw new PlayerRepositoryException("invalid numeric field " + field, exception);
        }
    }

    private static boolean isDuplicate(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1062) {
                return true;
            }
            String message = current.getMessage();
            if ("23000".equals(current.getSQLState())
                    && message != null
                    && (message.contains("uk_player_name") || message.contains("uk_player_account"))) {
                return true;
            }
        }
        return false;
    }

    private static void requireAccountId(long accountId) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("accountId must be positive");
        }
    }
}
