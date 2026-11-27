package com.example.project013982.like;

import com.example.project013982.common.ApiResponse;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups/{groupId}/diaries/{diaryId}/likes")
public class LikeController {
    private final LikeService likes;

    public LikeController(LikeService likes) {
        this.likes = likes;
    }

    @GetMapping
    public ApiResponse<LikeStatus> get(HttpServletRequest request, @PathVariable("groupId") String groupId,
                                       @PathVariable("diaryId") String diaryId) {
        return ApiResponse.success(likes.get(userId(request), parseId(groupId), parseId(diaryId)));
    }

    @PutMapping("/me")
    public ApiResponse<LikeStatus> add(HttpServletRequest request, @PathVariable("groupId") String groupId,
                                       @PathVariable("diaryId") String diaryId) {
        return ApiResponse.success(likes.add(userId(request), parseId(groupId), parseId(diaryId)));
    }

    @DeleteMapping("/me")
    public ApiResponse<LikeStatus> remove(HttpServletRequest request, @PathVariable("groupId") String groupId,
                                          @PathVariable("diaryId") String diaryId) {
        return ApiResponse.success(likes.remove(userId(request), parseId(groupId), parseId(diaryId)));
    }

    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(LoginRequiredException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(error.getMessage()));
    }

    @ExceptionHandler(DiaryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(DiaryNotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error.getMessage()));
    }

    private static long userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute("LOGIN_USER_ID");
        if (!(id instanceof Number number) || number.longValue() <= 0) {
            throw new LoginRequiredException();
        }
        return number.longValue();
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("올바른 일기장·일기 번호가 아닙니다.", error);
        }
    }
}
