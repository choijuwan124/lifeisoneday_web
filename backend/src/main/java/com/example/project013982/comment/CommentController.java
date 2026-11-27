package com.example.project013982.comment;

import com.example.project013982.comment.CommentService.CommentForbiddenException;
import com.example.project013982.comment.CommentService.CommentNotFoundException;
import com.example.project013982.common.ApiResponse;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Comment API under {@code /api/groups/{groupId}/diaries/{diaryId}/comments}.
 * IDs arrive as text so bad formats and overflow become 400 responses; the user comes from the session only.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/diaries/{diaryId}/comments")
public class CommentController {
    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    public ApiResponse<List<CommentResponse>> list(HttpServletRequest request,
                                                   @PathVariable("groupId") String groupId,
                                                   @PathVariable("diaryId") String diaryId) {
        return ApiResponse.success(comments.list(userId(request), parseId(groupId), parseId(diaryId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(HttpServletRequest request,
                                                               @PathVariable("groupId") String groupId,
                                                               @PathVariable("diaryId") String diaryId,
                                                               @RequestBody(required = false) CommentRequest body) {
        CommentResponse created = comments.create(userId(request), parseId(groupId), parseId(diaryId), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("댓글을 작성했습니다.", created));
    }

    @PutMapping("/{commentId}")
    public ApiResponse<CommentResponse> update(HttpServletRequest request,
                                               @PathVariable("groupId") String groupId,
                                               @PathVariable("diaryId") String diaryId,
                                               @PathVariable("commentId") String commentId,
                                               @RequestBody(required = false) CommentRequest body) {
        return ApiResponse.success("댓글을 수정했습니다.", comments.update(userId(request), parseId(groupId),
                parseId(diaryId), parseId(commentId), body));
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(HttpServletRequest request,
                                    @PathVariable("groupId") String groupId,
                                    @PathVariable("diaryId") String diaryId,
                                    @PathVariable("commentId") String commentId) {
        comments.delete(userId(request), parseId(groupId), parseId(diaryId), parseId(commentId));
        return ApiResponse.success("댓글을 삭제했습니다.", null);
    }

    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(LoginRequiredException error) {
        return failure(HttpStatus.UNAUTHORIZED, error.getMessage());
    }

    @ExceptionHandler({DiaryNotFoundException.class, CommentNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException error) {
        return failure(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(CommentForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(CommentForbiddenException error) {
        return failure(HttpStatus.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidJson(HttpMessageNotReadableException error) {
        return failure(HttpStatus.BAD_REQUEST, "댓글 요청은 올바른 JSON 형식이어야 합니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedType(HttpMediaTypeNotSupportedException error) {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "댓글 요청은 JSON으로 보내 주세요.");
    }

    /** Reads LOGIN_USER_ID without creating a session for anonymous requests. */
    private static long userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute("LOGIN_USER_ID");
        if (!(id instanceof Number number) || number.longValue() <= 0) throw new LoginRequiredException();
        return number.longValue();
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("올바른 일기장·일기·댓글 번호가 아닙니다.", error);
        }
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }
}
