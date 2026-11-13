package com.example.project013982.diary;

import com.example.project013982.member.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DiaryServiceTests {
    private MemoryDiaries diaries;
    private MemoryMembers members;
    private DiaryService service;

    @BeforeEach void setUp() {
        diaries = new MemoryDiaries();
        members = new MemoryMembers();
        members.memberIds.addAll(List.of("10:1", "10:2", "20:2"));
        service = new DiaryService(diaries, members);
    }

    @Test void createUsesAuthenticatedUserAndReturnsEditableDiary() {
        DiaryResponse created = service.create(1L, 10L, new CreateDiaryRequest("  오늘  ", "친구와 보낸 하루"));
        assertEquals(1L, created.authorId());
        assertEquals("오늘", created.title());
        assertTrue(created.canEdit());
        assertTrue(created.canDelete());
    }

    @Test void rejectsBlankAndOversizedContent() {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 10L, new CreateDiaryRequest("  ", "내용")));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 10L, new CreateDiaryRequest("제목", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 10L,
                new CreateDiaryRequest("x".repeat(201), "내용")));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 10L,
                new CreateDiaryRequest("제목", "x".repeat(10_001))));
        assertTrue(diaries.rows.isEmpty());
    }

    @Test void nonMembersAndUnauthenticatedUsersCannotReadOrCreate() {
        assertThrows(DiaryService.LoginRequiredException.class, () -> service.list(null, 10L));
        assertThrows(DiaryService.DiaryNotFoundException.class,
                () -> service.create(3L, 10L, new CreateDiaryRequest("제목", "내용")));
        DiaryResponse created = service.create(1L, 10L, new CreateDiaryRequest("제목", "내용"));
        assertThrows(DiaryService.DiaryNotFoundException.class, () -> service.get(2L, 20L, created.id()));
        assertThrows(DiaryService.DiaryNotFoundException.class, () -> service.get(3L, 10L, created.id()));
    }

    @Test void membersCanReadButOnlyAuthorCanEditOrDelete() {
        DiaryResponse created = service.create(1L, 10L, new CreateDiaryRequest("제목", "내용"));
        assertFalse(service.get(2L, 10L, created.id()).canEdit());
        assertThrows(DiaryService.DiaryForbiddenException.class,
                () -> service.update(2L, 10L, created.id(), new UpdateDiaryRequest("바꿈", "내용")));
        assertThrows(DiaryService.DiaryForbiddenException.class, () -> service.delete(2L, 10L, created.id()));
        DiaryResponse updated = service.update(1L, 10L, created.id(), new UpdateDiaryRequest("수정", "새 내용"));
        assertEquals("수정", updated.title());
        service.delete(1L, 10L, created.id());
        assertTrue(service.list(1L, 10L).isEmpty());
    }

    @Test void leavingGroupRemovesAccessWhileDiaryRemainsForOtherMembers() {
        DiaryResponse created = service.create(1L, 10L, new CreateDiaryRequest("남는 기록", "내용"));
        members.memberIds.remove("10:1");
        assertThrows(DiaryService.DiaryNotFoundException.class, () -> service.get(1L, 10L, created.id()));
        assertEquals("남는 기록", service.get(2L, 10L, created.id()).title());
    }

    private static final class MemoryMembers extends GroupMemberRepository {
        private final List<String> memberIds = new ArrayList<>();
        private MemoryMembers() { super(null); }
        @Override public boolean exists(Long groupId, Long userId) { return memberIds.contains(groupId + ":" + userId); }
    }

    private static final class MemoryDiaries extends DiaryRepository {
        private final List<Diary> rows = new ArrayList<>();
        private long nextId = 1;
        private MemoryDiaries() { super(null); }
        @Override public Diary save(Long groupId, Long authorId, String title, String content) {
            LocalDateTime now = LocalDateTime.now();
            Diary diary = new Diary(nextId++, groupId, authorId, title, content, now, now);
            rows.add(diary);
            return diary;
        }
        @Override public Optional<Diary> findByIdAndGroupId(Long diaryId, Long groupId) {
            return rows.stream().filter(d -> d.id().equals(diaryId) && d.groupId().equals(groupId)).findFirst();
        }
        @Override public Optional<DiaryResponse> findResponseByIdAndGroupId(Long diaryId, Long groupId) {
            return findByIdAndGroupId(diaryId, groupId).map(d -> new DiaryResponse(d.id(), d.groupId(), d.authorId(),
                    "member-" + d.authorId(), d.title(), d.content(), d.createdAt(), d.updatedAt(), false, false));
        }
        @Override public List<DiaryResponse> findAllByGroupId(Long groupId) {
            return rows.stream().filter(d -> d.groupId().equals(groupId)).map(d -> new DiaryResponse(d.id(), d.groupId(),
                    d.authorId(), "member-" + d.authorId(), d.title(), d.content(), d.createdAt(), d.updatedAt(), false, false)).toList();
        }
        @Override public int update(Long diaryId, Long groupId, Long authorId, String title, String content) {
            Optional<Diary> old = findByIdAndGroupId(diaryId, groupId).filter(d -> d.authorId().equals(authorId));
            if (old.isEmpty()) return 0;
            Diary d = old.get();
            rows.set(rows.indexOf(d), new Diary(d.id(), d.groupId(), d.authorId(), title, content, d.createdAt(), LocalDateTime.now()));
            return 1;
        }
        @Override public int delete(Long diaryId, Long groupId, Long authorId) {
            boolean removed = rows.removeIf(d -> d.id().equals(diaryId) && d.groupId().equals(groupId) && d.authorId().equals(authorId));
            return removed ? 1 : 0;
        }
    }
}
