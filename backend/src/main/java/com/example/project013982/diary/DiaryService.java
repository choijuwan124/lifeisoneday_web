package com.example.project013982.diary;

import com.example.project013982.member.GroupMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiaryService {
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 10_000;
    private final DiaryRepository diaryRepository;
    private final GroupMemberRepository memberRepository;

    public DiaryService(DiaryRepository diaryRepository, GroupMemberRepository memberRepository) {
        this.diaryRepository = diaryRepository;
        this.memberRepository = memberRepository;
    }

    public DiaryResponse create(Long userId, Long groupId, CreateDiaryRequest request) {
        requireLoggedIn(userId);
        requireValidGroup(groupId);
        requireMember(groupId, userId);
        if (request == null) throw new IllegalArgumentException("일기 내용을 입력해 주세요.");
        String title = normalize(request.title(), MAX_TITLE_LENGTH, "제목", true);
        String content = normalize(request.content(), MAX_CONTENT_LENGTH, "내용", false);
        Diary saved = diaryRepository.save(groupId, userId, title, content);
        return response(saved, true);
    }

    public List<DiaryResponse> list(Long userId, Long groupId) {
        requireLoggedIn(userId);
        requireValidGroup(groupId);
        requireMember(groupId, userId);
        return diaryRepository.findAllByGroupId(groupId);
    }

    public DiaryResponse get(Long userId, Long groupId, Long diaryId) {
        requireLoggedIn(userId);
        requireValidGroup(groupId);
        requireValidDiary(diaryId);
        requireMember(groupId, userId);
        return diaryRepository.findByIdAndGroupId(diaryId, groupId)
                .map(diary -> response(diary, diary.authorId().equals(userId)))
                .orElseThrow(DiaryNotFoundException::new);
    }

    public DiaryResponse update(Long userId, Long groupId, Long diaryId, UpdateDiaryRequest request) {
        requireLoggedIn(userId);
        requireValidGroup(groupId);
        requireValidDiary(diaryId);
        requireMember(groupId, userId);
        if (request == null) throw new IllegalArgumentException("수정할 일기 내용을 입력해 주세요.");
        Diary existing = diaryRepository.findByIdAndGroupId(diaryId, groupId)
                .orElseThrow(DiaryNotFoundException::new);
        if (!existing.authorId().equals(userId)) throw new DiaryForbiddenException();
        String title = normalize(request.title(), MAX_TITLE_LENGTH, "제목", true);
        String content = normalize(request.content(), MAX_CONTENT_LENGTH, "내용", false);
        if (diaryRepository.update(diaryId, groupId, userId, title, content) != 1)
            throw new DiaryNotFoundException();
        return diaryRepository.findByIdAndGroupId(diaryId, groupId)
                .map(diary -> response(diary, true)).orElseThrow(DiaryNotFoundException::new);
    }

    public void delete(Long userId, Long groupId, Long diaryId) {
        requireLoggedIn(userId);
        requireValidGroup(groupId);
        requireValidDiary(diaryId);
        requireMember(groupId, userId);
        Diary existing = diaryRepository.findByIdAndGroupId(diaryId, groupId)
                .orElseThrow(DiaryNotFoundException::new);
        if (!existing.authorId().equals(userId)) throw new DiaryForbiddenException();
        if (diaryRepository.delete(diaryId, groupId, userId) != 1) throw new DiaryNotFoundException();
    }
    private DiaryResponse response(Diary diary, boolean canEdit) {
        DiaryResponse base = diaryRepository.findResponseByIdAndGroupId(diary.id(), diary.groupId())
                .orElseThrow(DiaryNotFoundException::new);
        return new DiaryResponse(base.id(), base.groupId(), base.authorId(), base.authorNickname(),
                base.title(), base.content(), base.createdAt(), base.updatedAt(), canEdit, canEdit);
    }

    private void requireMember(Long groupId, Long userId) {
        if (!memberRepository.exists(groupId, userId)) throw new DiaryNotFoundException();
    }

    private static String normalize(String value, int max, String field, boolean strip) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "을(를) 입력해 주세요.");
        String normalized = strip ? value.strip() : value;
        if (normalized.length() > max) throw new IllegalArgumentException(field + "은(는) " + max + "자 이하여야 합니다.");
        return normalized;
    }

    private static void requireLoggedIn(Long id) {
        if (id == null || id <= 0) throw new LoginRequiredException();
    }
    private static void requireValidGroup(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("올바른 일기장 번호가 아닙니다.");
    }
    private static void requireValidDiary(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("올바른 일기 번호가 아닙니다.");
    }

    public static class LoginRequiredException extends RuntimeException { public LoginRequiredException() { super("로그인이 필요합니다."); } }
    public static class DiaryNotFoundException extends RuntimeException { public DiaryNotFoundException() { super("일기를 찾을 수 없거나 접근 권한이 없습니다."); } }
    public static class DiaryForbiddenException extends RuntimeException { public DiaryForbiddenException() { super("작성자만 일기를 수정하거나 삭제할 수 있습니다."); } }
}
