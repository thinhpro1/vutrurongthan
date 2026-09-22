package com.project.game.persistence.monster;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcMonsterRepository implements MonsterRepository {
    private static final String FIND_ALL_TEMPLATES_SQL =
            "SELECT id, name, level, hp, damage, potential_reward, range_move, speed, "
                    + "type_move, dart_id, icon_move, icon_attack, icon_injure, w, h "
                    + "FROM monster_template ORDER BY id";
    private static final String FIND_ALL_SPAWNS_SQL =
            "SELECT id, map_id, monster_id, x, y "
                    + "FROM monster_spawn ORDER BY map_id, id";

    private final DataSource dataSource;

    public JdbcMonsterRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<TemplateRow> findAllTemplates() {
        List<TemplateRow> templates = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(FIND_ALL_TEMPLATES_SQL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                templates.add(new TemplateRow(
                        results.getInt("id"),
                        results.getString("name"),
                        results.getInt("level"),
                        results.getLong("hp"),
                        results.getLong("damage"),
                        results.getLong("potential_reward"),
                        results.getInt("range_move"),
                        results.getInt("speed"),
                        results.getInt("type_move"),
                        results.getInt("dart_id"),
                        results.getString("icon_move"),
                        results.getString("icon_attack"),
                        results.getString("icon_injure"),
                        results.getInt("w"),
                        results.getInt("h")));
            }
            return List.copyOf(templates);
        } catch (SQLException exception) {
            throw new MonsterRepositoryException("failed to find monster templates", exception);
        }
    }

    @Override
    public List<SpawnRow> findAllSpawns() {
        List<SpawnRow> spawns = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(FIND_ALL_SPAWNS_SQL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                spawns.add(new SpawnRow(
                        results.getLong("id"),
                        results.getInt("map_id"),
                        results.getInt("monster_id"),
                        results.getInt("x"),
                        results.getInt("y")));
            }
            return List.copyOf(spawns);
        } catch (SQLException exception) {
            throw new MonsterRepositoryException("failed to find monster spawns", exception);
        }
    }
}
