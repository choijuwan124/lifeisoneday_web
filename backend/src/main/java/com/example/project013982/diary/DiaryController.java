package com.example.project013982.diary;

import com.example.project013982.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/diaries")
public class DiaryController {
    private final DiaryService service;
    public DiaryController(DiaryService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable String groupId, @RequestBody(required = false) CreateDiaryRequest body,
                                    HttpServletRequest request) {
        try {
            DiaryResponse created = service.create(userId(request), parse(groupId), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("일기를 작성했습니다.", created));
        } catch (DiaryService.LoginRequiredException e) { return failure(HttpStatus.UNAUTHORIZED, e.getMessage()); }
          catch (DiaryService.DiaryNotFoundException e) { return failure(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@PathVariable String groupId, HttpServletRequest request) {
        try {
            List<DiaryResponse> entries = service.list(userId(request), parse(groupId));
            return ResponseEntity.ok(ApiResponse.success(entries));
        } catch (DiaryService.LoginRequiredException e) { return failure(HttpStatus.UNAUTHORIZED, e.getMessage()); }
          catch (DiaryService.DiaryNotFoundException e) { return failure(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @GetMapping(value = "/{diaryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> detail(@PathVariable String groupId, @PathVariable String diaryId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.get(userId(request), parse(groupId), parse(diaryId))));
        } catch (DiaryService.LoginRequiredException e) { return failure(HttpStatus.UNAUTHORIZED, e.getMessage()); }
          catch (DiaryService.DiaryNotFoundException e) { return failure(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @PutMapping(value = "/{diaryId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable String groupId, @PathVariable String diaryId,
                                    @RequestBody(required = false) UpdateDiaryRequest body,
                                    HttpServletRequest request) {
        try {
            DiaryResponse updated = service.update(userId(request), parse(groupId), parse(diaryId), body);
            return ResponseEntity.ok(ApiResponse.success("일기를 수정했습니다.", updated));
        } catch (DiaryService.LoginRequiredException e) { return failure(HttpStatus.UNAUTHORIZED, e.getMessage()); }
          catch (DiaryService.DiaryForbiddenException e) { return failure(HttpStatus.FORBIDDEN, e.getMessage()); }
          catch (DiaryService.DiaryNotFoundException e) { return failure(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    @DeleteMapping(value = "/{diaryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable String groupId, @PathVariable String diaryId,
                                    HttpServletRequest request) {
        try {
            service.delete(userId(request), parse(groupId), parse(diaryId));
            return ResponseEntity.ok(ApiResponse.success("일기를 삭제했습니다.", null));
        } catch (DiaryService.LoginRequiredException e) { return failure(HttpStatus.UNAUTHORIZED, e.getMessage()); }
          catch (DiaryService.DiaryForbiddenException e) { return failure(HttpStatus.FORBIDDEN, e.getMessage()); }
          catch (DiaryService.DiaryNotFoundException e) { return failure(HttpStatus.NOT_FOUND, e.getMessage()); }
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("일기 요청은 올바른 JSON 형식이어야 합니다."));
    }

    private static Long userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("LOGIN_USER_ID");
        return value instanceof Number number ? number.longValue() : null;
    }
    private static Long parse(String value) {
        try { return Long.valueOf(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("올바른 번호가 아닙니다."); }
    }
    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }
}
