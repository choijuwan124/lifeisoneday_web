-- Apply after signup-schema.sql and group-schema.sql. Existing rows are preserved.
CREATE TABLE IF NOT EXISTS diaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_diaries_group_created_id (group_id, created_at, id),
    KEY idx_diaries_author_id (author_id),
    CONSTRAINT fk_diaries_group FOREIGN KEY (group_id) REFERENCES diary_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_diaries_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
