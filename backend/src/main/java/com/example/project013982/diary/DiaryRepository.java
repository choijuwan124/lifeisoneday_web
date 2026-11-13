package com.example.project013982.diary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class DiaryRepository {
    private static final RowMapper<Diary> DIARY_ROW_MAPPER = (rs, row) -> new Diary(
            rs.getLong("id"), rs.getLong("group_id"), rs.getLong("author_id"),
            rs.getString("title"), rs.getString("content"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());
    private static final String SELECT_DIARY = "SELECT d.id, d.group_id, d.author_id, d.title, d.content, "
            + "d.created_at, d.updated_at, u.nickname FROM diaries d "
            + "INNER JOIN users u ON u.id = d.author_id ";
    private static final RowMapper<DiaryResponse> RESPONSE_ROW_MAPPER = (rs, row) -> new DiaryResponse(
            rs.getLong("id"), rs.getLong("group_id"), rs.getLong("author_id"), rs.getString("nickname"),
            rs.getString("title"), rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(), false, false);

    private final JdbcTemplate jdbc;

    public DiaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Diary save(Long groupId, Long authorId, String title, String content) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO diaries (group_id, author_id, title, content) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, groupId);
            statement.setLong(2, authorId);
            statement.setString(3, title);
            statement.setString(4, content);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("일기 번호를 생성하지 못했습니다.");
        return findByIdAndGroupId(id.longValue(), groupId).orElseThrow();
    }

    public List<DiaryResponse> findAllByGroupId(Long groupId) {
        return jdbc.query(SELECT_DIARY + "WHERE d.group_id = ? ORDER BY d.created_at DESC, d.id DESC",
                RESPONSE_ROW_MAPPER, groupId);
    }

    public Optional<DiaryResponse> findResponseByIdAndGroupId(Long diaryId, Long groupId) {
        return jdbc.query(SELECT_DIARY + "WHERE d.id = ? AND d.group_id = ?", RESPONSE_ROW_MAPPER, diaryId, groupId)
                .stream().findFirst();
    }

    public Optional<Diary> findByIdAndGroupId(Long diaryId, Long groupId) {
        return jdbc.query("SELECT id, group_id, author_id, title, content, created_at, updated_at FROM diaries "
                + "WHERE id = ? AND group_id = ?", DIARY_ROW_MAPPER, diaryId, groupId).stream().findFirst();
    }
