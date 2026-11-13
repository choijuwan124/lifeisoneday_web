package com.example.project013982.diary;

import java.time.LocalDateTime;

/** Diary row stored through JdbcTemplate. */
public record Diary(Long id, Long groupId, Long authorId, String title, String content,
                    LocalDateTime createdAt, LocalDateTime updatedAt) { }
