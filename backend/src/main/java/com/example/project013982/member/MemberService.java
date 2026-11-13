package com.example.project013982.member;

import com.example.project013982.user.User;
import com.example.project013982.user.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Member list, friend invite and leave for a private group diary.
 *
 * <p>Policy for this stage:
 * <ul>
 *   <li>Only logged-in members of the group can see its members or invite friends.</li>
 *   <li>An invited friend is added immediately as MEMBER (no invite inbox or notification).</li>
 *   <li>A user can only remove their own membership. The OWNER cannot leave;
 *       ownership transfer and diary deletion are out of scope.</li>
 * </ul>
 */
@Service
public class MemberService {
    private static final int MAX_USERNAME_LENGTH = 50;

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public MemberService(GroupMemberRepository groupMemberRepository, UserRepository userRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public List<MemberResponse> listMembers(Long loginUserId, Long groupId) {
        requireLoggedIn(loginUserId);
        requireValidGroupId(groupId);
        requireMember(groupId, loginUserId);
        return groupMemberRepository.findMemberResponses(groupId);
    }

    @Transactional
    public MemberResponse invite(Long loginUserId, Long groupId, InviteMemberRequest request) {
        requireLoggedIn(loginUserId);
        requireValidGroupId(groupId);
        requireMember(groupId, loginUserId);
        String username = normalizeUsername(request == null ? null : request.username());

        User friend = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);
        if (groupMemberRepository.exists(groupId, friend.id())) {
            throw new AlreadyMemberException();
        }
        try {
            groupMemberRepository.addMember(groupId, friend.id());
        } catch (DuplicateKeyException exception) {
            // The UNIQUE (group_id, user_id) key also rejects concurrent duplicate invites.
            throw new AlreadyMemberException();
        }
        return groupMemberRepository.findMemberResponse(groupId, friend.id())
                .orElseThrow(() -> new IllegalStateException("초대한 멤버 정보를 확인하지 못했습니다."));
    }

    @Transactional
    public void leave(Long loginUserId, Long groupId) {
        requireLoggedIn(loginUserId);
        requireValidGroupId(groupId);
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, loginUserId)
                .orElseThrow(GroupNotAccessibleException::new);
        if (membership.isOwner()) {
            throw new OwnerCannotLeaveException();
        }
        // Deletes only this user's MEMBER row; other members and diaries stay.
        int deleted = groupMemberRepository.deleteMember(groupId, loginUserId);
        if (deleted == 0) {
            throw new GroupNotAccessibleException();
        }
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberRepository.exists(groupId, userId)) {
            throw new GroupNotAccessibleException();
        }
    }

    private static String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("초대할 친구의 아이디를 입력해 주세요.");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("아이디는 50자 이하여야 합니다.");
        }
        return normalized;
    }

    private static void requireValidGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException("올바른 단체 일기장 번호가 아닙니다.");
        }
    }

    private static void requireLoggedIn(Long userId) {
        if (userId == null || userId <= 0) {
            throw new LoginRequiredException();
        }
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

    public static final class UserNotFoundException extends RuntimeException {
        public UserNotFoundException() {
            super("해당 아이디로 가입한 사용자가 없습니다.");
        }
    }

    public static final class AlreadyMemberException extends RuntimeException {
        public AlreadyMemberException() {
            super("이미 이 단체 일기장에 참여 중인 사용자입니다.");
        }
    }

    public static final class OwnerCannotLeaveException extends RuntimeException {
        public OwnerCannotLeaveException() {
            super("방장은 단체 일기장에서 나갈 수 없습니다.");
        }
    }
}
