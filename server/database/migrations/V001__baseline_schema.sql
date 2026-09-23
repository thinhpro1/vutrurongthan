-- V001 is immutable executable schema history. Do not edit after release.
-- Catalog rows are intentionally not seeded by this migration.

CREATE TABLE IF NOT EXISTS account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    username VARCHAR(25) NOT NULL,

    password_hash VARBINARY(32) NOT NULL,
    password_salt VARBINARY(16) NOT NULL,

    role TINYINT UNSIGNED NOT NULL DEFAULT 0,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,

    ip_address VARCHAR(45) DEFAULT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_account_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player (
    id INT NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,

    name VARCHAR(10) NOT NULL,
    gender TINYINT UNSIGNED NOT NULL,

    power BIGINT NOT NULL,
    potential BIGINT NOT NULL,
    level INT UNSIGNED NOT NULL DEFAULT 1,
    exp BIGINT NOT NULL DEFAULT 0,

    base_stats JSON NOT NULL,
    current_stats JSON NOT NULL,

    hp INT NOT NULL,
    mp INT NOT NULL,

    appearance JSON NOT NULL,

    coin BIGINT NOT NULL,
    coin_lock BIGINT NOT NULL,
    diamond INT NOT NULL,
    ruby INT NOT NULL,

    position JSON NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    last_played_at TIMESTAMP NULL DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_player_name (name),
    UNIQUE KEY uk_player_account (account_id),

    CONSTRAINT fk_player_account
        FOREIGN KEY (account_id)
        REFERENCES account(id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS map_template (
    id SMALLINT UNSIGNED NOT NULL,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(16) NOT NULL,
    planet VARCHAR(16) NOT NULL,

    min_zone TINYINT UNSIGNED NOT NULL,
    max_zone TINYINT UNSIGNED NOT NULL,
    max_player TINYINT UNSIGNED NOT NULL,

    data SMALLINT UNSIGNED NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),

    CHECK (id <= 32767),
    CHECK (min_zone > 0),
    CHECK (max_zone >= min_zone),
    CHECK (max_player > 0),
    CHECK (data <= 32767)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS map_waypoint (
    id INT NOT NULL AUTO_INCREMENT,

    map_id SMALLINT UNSIGNED NOT NULL,

    x SMALLINT UNSIGNED NOT NULL,
    y SMALLINT UNSIGNED NOT NULL,
    type TINYINT UNSIGNED NOT NULL,

    go_map SMALLINT UNSIGNED NOT NULL,
    go_x SMALLINT UNSIGNED NOT NULL,
    go_y SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_map_waypoint_map
        FOREIGN KEY (map_id)
        REFERENCES map_template(id),

    CONSTRAINT fk_map_waypoint_go_map
        FOREIGN KEY (go_map)
        REFERENCES map_template(id),

    INDEX idx_map_waypoint_map (map_id),

    CHECK (map_id <= 32767),
    CHECK (go_map <= 32767),
    CHECK (x <= 32767),
    CHECK (y <= 32767),
    CHECK (go_x <= 32767),
    CHECK (go_y <= 32767),
    CHECK (type <= 2)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS monster_template (
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

CREATE TABLE IF NOT EXISTS monster_spawn (
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
