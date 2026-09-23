-- Reference/current schema snapshot only.
-- Executable runtime history lives under database/migrations/.

CREATE TABLE account (
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
