-- Reference/current schema snapshot only.
-- Executable runtime history lives under database/migrations/.

CREATE TABLE monster_template (
    id SMALLINT UNSIGNED NOT NULL,
    name VARCHAR(50) NOT NULL,

    level SMALLINT UNSIGNED NOT NULL,
    hp BIGINT NOT NULL,
    damage BIGINT NOT NULL,
    potential_reward BIGINT NOT NULL DEFAULT 0,

    range_move SMALLINT UNSIGNED NOT NULL,
    speed TINYINT UNSIGNED NOT NULL,
    type_move TINYINT UNSIGNED NOT NULL,

    dart_id TINYINT UNSIGNED NOT NULL,

    icon_move TEXT NOT NULL,
    icon_attack TEXT NOT NULL,
    icon_injure TEXT NOT NULL,

    w SMALLINT UNSIGNED NOT NULL,
    h SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (id),

    CHECK (id <= 32767),
    CHECK (level <= 32767),
    CHECK (hp > 0),
    CHECK (damage >= 0),
    CHECK (potential_reward >= 0),
    CHECK (range_move <= 32767),
    CHECK (speed <= 127),
    CHECK (type_move <= 2),
    CHECK (dart_id <= 127),
    CHECK (w <= 32767),
    CHECK (h <= 32767)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE monster_spawn (
    id INT NOT NULL AUTO_INCREMENT,

    map_id SMALLINT UNSIGNED NOT NULL,
    monster_id SMALLINT UNSIGNED NOT NULL,

    x SMALLINT UNSIGNED NOT NULL,
    y SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_monster_spawn_map
        FOREIGN KEY (map_id)
        REFERENCES map_template(id),

    CONSTRAINT fk_monster_spawn_template
        FOREIGN KEY (monster_id)
        REFERENCES monster_template(id),

    INDEX idx_monster_spawn_map (map_id),

    CHECK (map_id <= 32767),
    CHECK (monster_id <= 32767),
    CHECK (x <= 32767),
    CHECK (y <= 32767)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
