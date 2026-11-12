-- MySQL setup for private group diaries. Apply after signup-schema.sql.
-- Existing application tables and rows are preserved by CREATE TABLE IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS diary_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_diary_groups_owner_id (owner_id),
    CONSTRAINT fk_diary_groups_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary_group_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_group_members_group_user (group_id, user_id),
    KEY idx_diary_group_members_user_group (user_id, group_id),
    CONSTRAINT chk_diary_group_members_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT fk_diary_group_members_group
        FOREIGN KEY (group_id) REFERENCES diary_groups (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_diary_group_members_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
