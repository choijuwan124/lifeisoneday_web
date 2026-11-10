package com.example.project013982.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByUsername(String username) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
        return count != null && count > 0;
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, nickname FROM users WHERE username = ?",
                (resultSet, rowNumber) -> new User(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("nickname")),
                username);
        return users.stream().findFirst();
    }

    public User save(String username, String passwordHash, String nickname) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash, nickname) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("회원 식별자를 생성하지 못했습니다.");
        }
        return new User(id.longValue(), username, passwordHash, nickname);
    }
}
