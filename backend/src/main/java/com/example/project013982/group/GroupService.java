package com.example.project013982.group;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional
    public GroupResponse create(Long userId, CreateGroupRequest request) {
        requireLoggedIn(userId);
        if (request == null) {
            throw new IllegalArgumentException("단체 일기장 정보를 입력해 주세요.");
        }

        String name = normalizeName(request.name());
        String description = normalizeDescription(request.description());

        DiaryGroup group = groupRepository.save(name, description, userId);
        groupRepository.addOwnerMember(group.id(), userId);
        return toResponse(group);
    }

    public List<GroupResponse> listForUser(Long userId) {
        requireLoggedIn(userId);
        return groupRepository.findAllByMemberId(userId).stream()
                .map(GroupService::toResponse)
                .toList();
    }

    public GroupResponse getForUser(Long userId, Long groupId) {
        requireLoggedIn(userId);
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException("올바른 단체 일기장 번호가 아닙니다.");
        }
        return groupRepository.findByIdAndMemberId(groupId, userId)
                .map(GroupService::toResponse)
                .orElseThrow(GroupNotAccessibleException::new);
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("일기장 이름을 입력해 주세요.");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("일기장 이름은 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("일기장 설명은 500자 이하여야 합니다.");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireLoggedIn(Long userId) {
        if (userId == null || userId <= 0) {
            throw new LoginRequiredException();
        }
    }

    private static GroupResponse toResponse(DiaryGroup group) {
        return new GroupResponse(
                group.id(),
                group.name(),
                group.description(),
                group.ownerId(),
                group.createdAt());
    }

    public static final class LoginRequiredException extends RuntimeException {
        public LoginRequiredException() {
            super("로그인이 필요합니다.");
        }
    }

    public static final class GroupNotAccessibleException extends RuntimeException {
        public GroupNotAccessibleException() {
            super("단체 일기장을 찾을 수 없거나 접근 권한이 없습니다.");
        }
    }
}
