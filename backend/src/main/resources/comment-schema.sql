-- Apply after signup-schema.sql, group-schema.sql and diary-schema.sql.
CREATE TABLE IF NOT EXISTS diary_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_diary_comments_diary_created (diary_id, created_at, id),
    KEY idx_diary_comments_author (author_id),
    CONSTRAINT chk_diary_comments_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 1000),
    CONSTRAINT fk_diary_comments_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
