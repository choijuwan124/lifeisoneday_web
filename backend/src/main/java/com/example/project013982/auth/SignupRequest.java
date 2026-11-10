package com.example.project013982.auth;

/** Signup JSON fields. Required values and length limits are checked by UserService. */
public record SignupRequest(String username, String password, String nickname) {
    @Override
    public String toString() {
        return "SignupRequest[username=" + username + ", password=[REDACTED], nickname=" + nickname + "]";
    }
}
