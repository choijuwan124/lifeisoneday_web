package com.example.project013982.comment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to diary_comments (comment-schema.sql).
 * Every statement uses bound parameters. Writes are always limited by comment, diary and author ID,
 * and callers check the affected row count, so another diary's or user's comment is never changed.
 */
@Repository
public class CommentRepository {
    // Only users.nickname is joined: usernames and password hashes are never selected.
    private static final String SELECT_COMMENT = "SELECT c.id, c.diary_id, c.author_id, u.nickname, c.content, "
            + "c.created_at, c.updated_at FROM diary_comments c INNER JOIN users u ON u.id = c.author_id ";
    private static final RowMapper<Comment> COMMENT_ROW_MAPPER = (rs, row) -> new Comment(
            rs.getLong("id"), rs.getLong("diary_id"), rs.getLong("author_id"), rs.getString("nickname"),
            rs.getString("content"), toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public CommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a comment and returns its generated ID. */
    public long save(long diaryId, long authorId, String content) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO diary_comments (diary_id, author_id, content) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, diaryId);
            statement.setLong(2, authorId);
            statement.setString(3, content);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("댓글 번호를 생성하지 못했습니다.");
        return id.longValue();
    }

    /** Oldest first; comments created in the same instant keep their insertion (ID) order. */
    public List<Comment> findAllByDiaryId(long diaryId) {
        return jdbc.query(SELECT_COMMENT + "WHERE c.diary_id = ? ORDER BY c.created_at ASC, c.id ASC",
                COMMENT_ROW_MAPPER, diaryId);
    }

    /** Finds a comment only when it belongs to the requested diary. */
    public Optional<Comment> findByIdAndDiaryId(long commentId, long diaryId) {
        return jdbc.query(SELECT_COMMENT + "WHERE c.id = ? AND c.diary_id = ?",
                COMMENT_ROW_MAPPER, commentId, diaryId).stream().findFirst();
    }

    /** Updates the content only for this comment, diary and author; returns the affected row count. */
    public int update(long commentId, long diaryId, long authorId, String content) {
        return jdbc.update("UPDATE diary_comments SET content = ?, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE id = ? AND diary_id = ? AND author_id = ?", content, commentId, diaryId, authorId);
    }

    /** Deletes only this comment of this diary written by this author; returns the affected row count. */
    public int delete(long commentId, long diaryId, long authorId) {
        return jdbc.update("DELETE FROM diary_comments WHERE id = ? AND diary_id = ? AND author_id = ?",
                commentId, diaryId, authorId);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
