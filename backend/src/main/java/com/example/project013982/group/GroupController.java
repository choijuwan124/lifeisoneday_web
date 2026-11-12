package com.example.project013982.group;

import com.example.project013982.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestBody(required = false) CreateGroupRequest request,
            HttpSession session) {
        try {
            GroupResponse group = groupService.create(loginUserId(session), request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("단체 일기장이 생성되었습니다.", group));
        } catch (GroupService.LoginRequiredException exception) {
            return failure(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpSession session) {
        try {
            List<GroupResponse> groups = groupService.listForUser(loginUserId(session));
            return ResponseEntity.ok(ApiResponse.success(groups));
        } catch (GroupService.LoginRequiredException exception) {
            return failure(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }

    @GetMapping(value = "/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> detail(@PathVariable String groupId, HttpSession session) {
        try {
            GroupResponse group = groupService.getForUser(loginUserId(session), parseGroupId(groupId));
            return ResponseEntity.ok(ApiResponse.success(group));
        } catch (GroupService.LoginRequiredException exception) {
            return failure(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (GroupService.GroupNotAccessibleException exception) {
            return failure(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("단체 일기장 요청은 올바른 JSON 형식이어야 합니다."));
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
