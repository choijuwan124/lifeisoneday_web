package com.example.project013982.member;

/** Request body for inviting a registered friend by username. Validation is handled by MemberService. */
public record InviteMemberRequest(String username) {
}
