-- Apply after signup-schema.sql, group-schema.sql and diary-schema.sql.
-- Database CASCADE removes metadata only. The photo service must clean up stored files.
CREATE TABLE IF NOT EXISTS diary_photos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    storage_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_photos_diary (diary_id),
    UNIQUE KEY uk_diary_photos_storage (storage_key),
    CONSTRAINT chk_diary_photos_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_diary_photos_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT fk_diary_photos_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
