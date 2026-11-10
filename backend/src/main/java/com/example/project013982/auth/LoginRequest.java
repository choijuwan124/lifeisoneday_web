package com.example.project013982.auth;

/** Login JSON fields. Validation is handled by UserService. */
public record LoginRequest(String username, String password) {
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[REDACTED]]";
    }
}
