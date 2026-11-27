package com.example.project013982.comment;

import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import com.example.project013982.member.GroupMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Comment rules from docs/social-feature-contract.md.
 *
 * <ul>
 *   <li>The author is always the session user; request bodies never choose it.</li>
 *   <li>Only current members can read or write, and the diary must belong to the requested group.</li>
 *   <li>Only the author, while still a member, can edit or delete. Owners have no extra rights.</li>
 *   <li>Leaving a group blocks access but keeps the comments already written.</li>
 * </ul>
 */
@Service
public class CommentService {
    static final int MAX_CONTENT_LENGTH = 1000;

    private final CommentRepository comments;
    private final DiaryRepository diaries;
    private final GroupMemberRepository members;

    public CommentService(CommentRepository comments, DiaryRepository diaries, GroupMemberRepository members) {
        this.comments = comments;
        this.diaries = diaries;
        this.members = members;
    }

    public List<CommentResponse> list(Long userId, long groupId, long diaryId) {
        long viewer = requireAccess(userId, groupId, diaryId);
        return comments.findAllByDiaryId(diaryId).stream()
                .map(comment -> CommentResponse.of(comment, viewer))
                .toList();
    }

    public CommentResponse create(Long userId, long groupId, long diaryId, CommentRequest request) {
        long author = requireAccess(userId, groupId, diaryId);
        String content = normalizeContent(request);
        long id = comments.save(diaryId, author, content);
        return comments.findByIdAndDiaryId(id, diaryId)
                .map(comment -> CommentResponse.of(comment, author))
                .orElseThrow(CommentNotFoundException::new);
    }

    public CommentResponse update(Long userId, long groupId, long diaryId, long commentId, CommentRequest request) {
        long author = requireAccess(userId, groupId, diaryId);
        requireOwnComment(author, diaryId, commentId);
        String content = normalizeContent(request);
        if (comments.update(commentId, diaryId, author, content) != 1) throw new CommentNotFoundException();
        return comments.findByIdAndDiaryId(commentId, diaryId)
                .map(comment -> CommentResponse.of(comment, author))
                .orElseThrow(CommentNotFoundException::new);
    }

    public void delete(Long userId, long groupId, long diaryId, long commentId) {
        long author = requireAccess(userId, groupId, diaryId);
        requireOwnComment(author, diaryId, commentId);
        if (comments.delete(commentId, diaryId, author) != 1) throw new CommentNotFoundException();
    }

    /** Validates the IDs and membership; returns the session user ID. */
    private long requireAccess(Long userId, long groupId, long diaryId) {
        if (userId == null || userId <= 0) throw new LoginRequiredException();
        if (groupId <= 0 || diaryId <= 0) throw new IllegalArgumentException("올바른 일기장·일기 번호가 아닙니다.");
        if (!members.exists(groupId, userId) || diaries.findByIdAndGroupId(diaryId, groupId).isEmpty()) {
            throw new DiaryNotFoundException();
        }
        return userId;
    }

    /** A comment of another diary is reported as missing (404); another author's comment as forbidden (403). */
    private void requireOwnComment(long userId, long diaryId, long commentId) {
        if (commentId <= 0) throw new IllegalArgumentException("올바른 댓글 번호가 아닙니다.");
        Comment comment = comments.findByIdAndDiaryId(commentId, diaryId).orElseThrow(CommentNotFoundException::new);
        if (comment.authorId() != userId) throw new CommentForbiddenException();
    }

    /** Trims both ends and accepts 1 to 1000 characters, counted like MySQL CHAR_LENGTH (code points). */
    static String normalizeContent(CommentRequest request) {
        if (request == null || request.content() == null) throw new IllegalArgumentException("댓글 내용을 입력해 주세요.");
        if (!(request.content() instanceof String raw)) throw new IllegalArgumentException("댓글 내용은 문자열이어야 합니다.");
        String content = raw.strip();
        if (content.isEmpty()) throw new IllegalArgumentException("댓글 내용을 입력해 주세요.");
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_CONTENT_LENGTH + "자 이하로 입력해 주세요.");
        }
        return content;
    }

    public static class CommentNotFoundException extends RuntimeException {
        public CommentNotFoundException() { super("댓글을 찾을 수 없습니다."); }
    }

    public static class CommentForbiddenException extends RuntimeException {
        public CommentForbiddenException() { super("작성자만 댓글을 수정하거나 삭제할 수 있습니다."); }
    }
}
