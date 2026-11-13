package com.example.project013982.member;

import java.time.LocalDateTime;

/**
 * Safe representation of a group diary member.
 * It never contains the username, password or password hash of the account.
 */
public record MemberResponse(
        Long userId,
        String nickname,
        String role,
        LocalDateTime joinedAt) {
}
