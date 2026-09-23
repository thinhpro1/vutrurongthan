-- Reference/current schema snapshot only.
-- Executable runtime history lives under database/migrations/.

CREATE TABLE map_template (
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

CREATE TABLE map_waypoint (
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
