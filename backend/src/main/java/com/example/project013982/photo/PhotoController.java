package com.example.project013982.photo;

import com.example.project013982.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@RestController
@RequestMapping("/api/groups/{groupId}/diaries/{diaryId}/photo")
public class PhotoController {
    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PhotoResponse>> metadata(@PathVariable String groupId,
                                                                 @PathVariable String diaryId,
                                                                 HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getMetadata(userId(request), parse(groupId), parse(diaryId))
                .orElse(null)));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoResponse>> replace(@PathVariable String groupId,
                                                               @PathVariable String diaryId,
                                                               @RequestPart(name = "file", required = false) MultipartFile file,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("사진을 저장했습니다.",
                service.replace(userId(request), parse(groupId), parse(diaryId), file)));
    }

    @GetMapping("/content")
    public ResponseEntity<byte[]> content(@PathVariable String groupId, @PathVariable String diaryId,
                                          HttpServletRequest request) {
        PhotoService.PhotoContent photo = service.getContent(userId(request), parse(groupId), parse(diaryId));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(photo.contentType()));
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(photo.bytes(), headers, HttpStatus.OK);
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String groupId, @PathVariable String diaryId,
                                                     HttpServletRequest request) {
        service.delete(userId(request), parse(groupId), parse(diaryId));
        return ResponseEntity.ok(ApiResponse.success("사진을 삭제했습니다.", null));
    }

    @ExceptionHandler(PhotoService.LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> loginRequired(RuntimeException exception) {
        return failure(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(PhotoService.PhotoAccessNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException exception) {
        return failure(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PhotoService.PhotoForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(RuntimeException exception) {
        return failure(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(PhotoService.PhotoNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> missingPhoto(RuntimeException exception) {
        return failure(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PhotoStorage.UnsupportedPhotoException.class)
    public ResponseEntity<ApiResponse<Void>> unsupported(RuntimeException exception) {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    @ExceptionHandler(PhotoStorage.PhotoTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(RuntimeException exception) {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage());
    }

    private static Long userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("LOGIN_USER_ID");
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long parse(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("올바른 번호가 아닙니다.");
        }
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(Objects.requireNonNullElse(message, "요청을 처리하지 못했습니다.")));
    }
}
