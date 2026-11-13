package com.example.project013982.member;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to diary_group_members (created by group-schema.sql).
 * The (group_id, user_id) UNIQUE key rejects duplicate memberships, including concurrent invites.
 */
@Repository
public class GroupMemberRepository {
    private static final RowMapper<GroupMember> MEMBER_ROW_MAPPER = (resultSet, rowNumber) ->
            new GroupMember(
                    resultSet.getLong("id"),
                    resultSet.getLong("group_id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("role"),
                    toLocalDateTime(resultSet.getTimestamp("joined_at")));

    // Only safe columns are selected: users.username and users.password_hash are never read here.
    private static final String MEMBER_RESPONSE_SQL =
            "SELECT gm.user_id, u.nickname, gm.role, gm.joined_at "
                    + "FROM diary_group_members gm "
                    + "INNER JOIN users u ON u.id = gm.user_id ";
    private static final RowMapper<MemberResponse> MEMBER_RESPONSE_ROW_MAPPER = (resultSet, rowNumber) ->
            new MemberResponse(
                    resultSet.getLong("user_id"),
                    resultSet.getString("nickname"),
                    resultSet.getString("role"),
                    toLocalDateTime(resultSet.getTimestamp("joined_at")));

    private final JdbcTemplate jdbcTemplate;

    public GroupMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId) {
        List<GroupMember> members = jdbcTemplate.query(
                "SELECT id, group_id, user_id, role, joined_at FROM diary_group_members "
                        + "WHERE group_id = ? AND user_id = ?",
                MEMBER_ROW_MAPPER,
                groupId,
                userId);
        return members.stream().findFirst();
    }

    public boolean exists(Long groupId, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM diary_group_members WHERE group_id = ? AND user_id = ?",
                Long.class,
                groupId,
                userId);
        return count != null && count > 0;
    }

    /** Owner first, then the order in which members joined. */
    public List<MemberResponse> findMemberResponses(Long groupId) {
        return jdbcTemplate.query(
                MEMBER_RESPONSE_SQL
                        + "WHERE gm.group_id = ? "
                        + "ORDER BY CASE WHEN gm.role = 'OWNER' THEN 0 ELSE 1 END, gm.joined_at, gm.id",
                MEMBER_RESPONSE_ROW_MAPPER,
                groupId);
    }

    public Optional<MemberResponse> findMemberResponse(Long groupId, Long userId) {
        List<MemberResponse> members = jdbcTemplate.query(
                MEMBER_RESPONSE_SQL + "WHERE gm.group_id = ? AND gm.user_id = ?",
                MEMBER_RESPONSE_ROW_MAPPER,
                groupId,
                userId);
        return members.stream().findFirst();
    }

    /** Throws DuplicateKeyException when the user is already a member of the group. */
    public void addMember(Long groupId, Long userId) {
        jdbcTemplate.update(
                "INSERT INTO diary_group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')",
                groupId,
                userId);
    }

    /**
     * Deletes only the caller's own MEMBER row. OWNER rows, other members and diary data are untouched.
     *
     * @return the number of deleted rows (0 or 1)
     */
    public int deleteMember(Long groupId, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM diary_group_members WHERE group_id = ? AND user_id = ? AND role = 'MEMBER'",
                groupId,
                userId);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
