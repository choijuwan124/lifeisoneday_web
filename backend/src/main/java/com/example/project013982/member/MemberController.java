package com.example.project013982.member;

import com.example.project013982.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/groups/{groupId}/members     member list (200)
 * POST   /api/groups/{groupId}/members     invite {"username":"friend01"} (201)
 * DELETE /api/groups/{groupId}/members/me  leave the group (200)
 *
 * <p>Status codes: 400 bad input, 401 not logged in, 403 owner cannot leave,
 * 404 group not accessible or user not found, 409 already a member.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<MemberResponse>>> list(
            @PathVariable String groupId, HttpSession session) {
        List<MemberResponse> members = memberService.listMembers(loginUserId(session), parseGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MemberResponse>> invite(
            @PathVariable String groupId,
            @RequestBody(required = false) InviteMemberRequest request,
            HttpSession session) {
        MemberResponse member = memberService.invite(loginUserId(session), parseGroupId(groupId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("친구를 단체 일기장에 초대했습니다.", member));
    }

    @DeleteMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable String groupId, HttpSession session) {
        memberService.leave(loginUserId(session), parseGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.success("단체 일기장에서 나갔습니다.", null));
    }

    @ExceptionHandler(MemberService.LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginRequired(RuntimeException exception) {
        return failure(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({MemberService.GroupNotAccessibleException.class, MemberService.UserNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException exception) {
        return failure(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MemberService.AlreadyMemberException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyMember(RuntimeException exception) {
        return failure(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MemberService.OwnerCannotLeaveException.class)
    public ResponseEntity<ApiResponse<Void>> handleOwnerCannotLeave(RuntimeException exception) {
        return failure(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(HttpMessageNotReadableException exception) {
        return failure(HttpStatus.BAD_REQUEST, "친구 초대 요청은 올바른 JSON 형식이어야 합니다.");
    }

    private static Long loginUserId(HttpSession session) {
        Object value = session.getAttribute("LOGIN_USER_ID");
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long parseGroupId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("올바른 단체 일기장 번호가 아닙니다.");
        }
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }
}
