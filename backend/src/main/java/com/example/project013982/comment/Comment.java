package com.example.project013982.comment;

import java.time.LocalDateTime;

/**
 * One row of diary_comments (comment-schema.sql), read together with the author's nickname.
 * Only users.nickname is joined: usernames and password hashes are never read for comments.
 */
public record Comment(
        long id,
        long diaryId,
        long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
