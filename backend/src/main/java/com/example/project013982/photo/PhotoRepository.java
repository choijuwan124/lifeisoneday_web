package com.example.project013982.photo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** JDBC operations for photos. Call mutating methods inside a transaction. */
@Repository
public class PhotoRepository {
    private static final RowMapper<Photo> PHOTO_ROW = (rs, row) -> new Photo(
            rs.getLong("id"), rs.getLong("diary_id"), rs.getString("storage_key"),
            rs.getString("content_type"), rs.getLong("size_bytes"),
            toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public PhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializes photo mutations and validates the group/diary association. */
    public Optional<DiaryOwner> lockDiary(Long groupId, Long diaryId) {
        return jdbc.query("SELECT group_id, author_id FROM diaries WHERE id = ? AND group_id = ? FOR UPDATE",
                (rs, row) -> new DiaryOwner(rs.getLong("group_id"), rs.getLong("author_id")), diaryId, groupId)
                .stream().findFirst();
    }

    /** Locks the current membership row so a leave cannot race an authorized operation. */
    public boolean lockMembership(Long groupId, Long userId) {
        return !jdbc.query("SELECT id FROM diary_group_members WHERE group_id = ? AND user_id = ? FOR UPDATE",
                (rs, row) -> rs.getLong("id"), groupId, userId).isEmpty();
    }

    public Optional<Photo> findByDiaryId(Long diaryId) {
        return jdbc.query("SELECT id, diary_id, storage_key, content_type, size_bytes, created_at, updated_at "
                + "FROM diary_photos WHERE diary_id = ?", PHOTO_ROW, diaryId).stream().findFirst();
    }

    /** Insert or replace the single row. lockDiary must be held by the caller. */
    public Photo save(Long diaryId, String storageKey, String contentType, long sizeBytes) {
        int updated = jdbc.update("UPDATE diary_photos SET storage_key = ?, content_type = ?, size_bytes = ?, "
                        + "updated_at = CURRENT_TIMESTAMP(6) WHERE diary_id = ?",
                storageKey, contentType, sizeBytes, diaryId);
        if (updated == 0) {
            jdbc.update("INSERT INTO diary_photos (diary_id, storage_key, content_type, size_bytes) VALUES (?, ?, ?, ?)",
                    diaryId, storageKey, contentType, sizeBytes);
        }
        return findByDiaryId(diaryId).orElseThrow(() -> new IllegalStateException("사진 정보를 저장하지 못했습니다."));
    }

    public int deleteByDiaryId(Long diaryId) {
        return jdbc.update("DELETE FROM diary_photos WHERE diary_id = ?", diaryId);
    }

    public List<String> storageKeysForDiary(Long diaryId) {
        return jdbc.query("SELECT storage_key FROM diary_photos WHERE diary_id = ?", (rs, row) -> rs.getString(1), diaryId);
    }

    public List<String> storageKeysForGroup(Long groupId) {
        return jdbc.query("SELECT p.storage_key FROM diary_photos p INNER JOIN diaries d ON d.id = p.diary_id "
                + "WHERE d.group_id = ? ORDER BY d.id FOR UPDATE", (rs, row) -> rs.getString(1), groupId);
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record DiaryOwner(Long groupId, Long authorId) { }
}
