-- Reference/current schema snapshot only.
-- Executable runtime history lives under database/migrations/.

CREATE TABLE player (
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
