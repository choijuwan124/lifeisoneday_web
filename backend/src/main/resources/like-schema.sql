-- Apply after signup-schema.sql, group-schema.sql and diary-schema.sql.
-- IF NOT EXISTS preserves rows; it does not retrofit constraints on an older table.
CREATE TABLE IF NOT EXISTS diary_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_diary_likes_user (user_id),
    CONSTRAINT fk_diary_likes_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
