package com.example.project013982.member;

import java.time.LocalDateTime;

/** One row of diary_group_members. role is OWNER or MEMBER. */
public record GroupMember(
        Long id,
        Long groupId,
        Long userId,
        String role,
        LocalDateTime joinedAt) {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }
}
