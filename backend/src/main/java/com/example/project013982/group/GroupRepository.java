package com.example.project013982.group;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class GroupRepository {
    private static final String GROUP_COLUMNS = "g.id, g.name, g.description, g.owner_id, g.created_at";
    private static final RowMapper<DiaryGroup> GROUP_ROW_MAPPER = (resultSet, rowNumber) ->
            new DiaryGroup(
                    resultSet.getLong("id"),
                    resultSet.getString("name"),
                    resultSet.getString("description"),
                    resultSet.getLong("owner_id"),
                    resultSet.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public GroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DiaryGroup save(String name, String description, Long ownerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO diary_groups (name, description, owner_id) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            if (description == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, description);
            }
            statement.setLong(3, ownerId);
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("단체 일기장 식별자를 생성하지 못했습니다.");
        }
        return findById(id.longValue())
                .orElseThrow(() -> new IllegalStateException("생성한 단체 일기장을 확인하지 못했습니다."));
    }

    public void addOwnerMember(Long groupId, Long ownerId) {
        jdbcTemplate.update(
                "INSERT INTO diary_group_members (group_id, user_id, role) VALUES (?, ?, 'OWNER')",
                groupId,
                ownerId);
    }

    public List<DiaryGroup> findAllByMemberId(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + GROUP_COLUMNS + " FROM diary_groups g "
                        + "INNER JOIN diary_group_members gm ON gm.group_id = g.id "
                        + "WHERE gm.user_id = ? ORDER BY g.created_at DESC, g.id DESC",
                GROUP_ROW_MAPPER,
                userId);
    }

    public Optional<DiaryGroup> findByIdAndMemberId(Long groupId, Long userId) {
        List<DiaryGroup> groups = jdbcTemplate.query(
                "SELECT " + GROUP_COLUMNS + " FROM diary_groups g "
                        + "INNER JOIN diary_group_members gm ON gm.group_id = g.id "
                        + "WHERE g.id = ? AND gm.user_id = ?",
                GROUP_ROW_MAPPER,
                groupId,
                userId);
        return groups.stream().findFirst();
    }

    private Optional<DiaryGroup> findById(Long groupId) {
        List<DiaryGroup> groups = jdbcTemplate.query(
                "SELECT id, name, description, owner_id, created_at FROM diary_groups WHERE id = ?",
                (resultSet, rowNumber) -> new DiaryGroup(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getLong("owner_id"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()),
                groupId);
        return groups.stream().findFirst();
    }
}
