package com.example.project013982.like;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.regex.Pattern;

@Repository
public class LikeRepository {
    private static final Pattern LIKE_UNIQUE_KEY =
            Pattern.compile(" for key '[^']*\\buk_diary_likes_diary_user'$");

    private final JdbcTemplate jdbc;

    public LikeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long diaryId, long userId) {
        jdbc.update("INSERT INTO diary_likes (diary_id, user_id) VALUES (?, ?)", diaryId, userId);
    }

    public void remove(long diaryId, long userId) {
        jdbc.update("DELETE FROM diary_likes WHERE diary_id = ? AND user_id = ?", diaryId, userId);
    }

    public LikeStatus status(long diaryId, long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS like_count, COUNT(CASE WHEN user_id = ? THEN 1 END) AS my_count
                FROM diary_likes
                WHERE diary_id = ?
                """, (row, number) -> new LikeStatus(diaryId, row.getLong("like_count"),
                row.getLong("my_count") > 0), userId, diaryId);
    }
}
