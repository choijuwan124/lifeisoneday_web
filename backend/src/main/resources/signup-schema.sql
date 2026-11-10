-- MySQL setup for the signup feature (run once before starting the backend).
-- 1. Create/select your team's lifeisoneday database.
-- 2. Run: mysql -u YOUR_MYSQL_USERNAME -p lifeisoneday < signup-schema.sql
--    Use this file's full path, or run from backend/src/main/resources.
-- 3. Configure application-local.properties using the existing example.
-- This file is intentionally not auto-executed; it does not alter existing tables.
-- If users already exists, verify its columns and UNIQUE constraint before use.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
