package com.example.project013982.auth;

import com.example.project013982.common.ApiResponse;
import com.example.project013982.user.User;
import com.example.project013982.user.UserService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService userService;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    public ApiResponse<Void> signup(SignupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("회원가입 정보를 입력해 주세요.");
        }
        userService.register(request.username(), request.password(), request.nickname());
        // Account details and password hashes must not be included in the response.
        return ApiResponse.success("회원가입이 완료되었습니다. 로그인해 주세요.", null);
    }

    public User login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("로그인 정보를 입력해 주세요.");
        }
        return userService.authenticate(request.username(), request.password());
    }
}
