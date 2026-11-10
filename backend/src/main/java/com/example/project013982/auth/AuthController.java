package com.example.project013982.auth;

import com.example.project013982.common.ApiResponse;
import com.example.project013982.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody(required = false) SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
            @RequestBody(required = false) LoginRequest request, HttpSession session) {
        User user = authService.login(request);
        session.setAttribute("LOGIN_USER_ID", user.id());
        session.setAttribute("LOGIN_USER_NICKNAME", user.nickname());
        return ResponseEntity.ok(ApiResponse.success("로그인되었습니다.", Map.of("nickname", user.nickname())));
    }

    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다.", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSignupJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("회원가입 요청은 올바른 JSON 형식이어야 합니다."));
    }
}
