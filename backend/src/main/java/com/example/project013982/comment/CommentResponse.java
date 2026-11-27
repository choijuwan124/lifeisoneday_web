package com.example.project013982.comment;

import java.time.LocalDateTime;

/**
 * Comment data sent to the frontend. canEdit and canDelete are true only for the author;
 * group owners get no extra rights over other people's comments.
 */
public record CommentResponse(
        long id,
        long diaryId,
        long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete) {

    /** Builds the response seen by {@code viewerId}, who must already be a current group member. */
    public static CommentResponse of(Comment comment, long viewerId) {
        boolean author = comment.authorId() == viewerId;
        return new CommentResponse(comment.id(), comment.diaryId(), comment.authorId(), comment.authorNickname(),
                comment.content(), comment.createdAt(), comment.updatedAt(), author, author);
    }
}
