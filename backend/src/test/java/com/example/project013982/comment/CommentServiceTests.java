package com.example.project013982.comment;

import com.example.project013982.comment.CommentService.CommentForbiddenException;
import com.example.project013982.comment.CommentService.CommentNotFoundException;
import com.example.project013982.diary.Diary;
import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import com.example.project013982.member.GroupMemberRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentServiceTests {
    private static final long GROUP = 4;
    private static final long DIARY = 7;
    private static final long ME = 9;
    private static final long FRIEND = 10;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 27, 10, 0);
    private static final LocalDateTime EDITED = LocalDateTime.of(2026, 11, 27, 11, 30);

    private final CommentRepository comments = mock(CommentRepository.class);
    private final DiaryRepository diaries = mock(DiaryRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final CommentService service = new CommentService(comments, diaries, members);

    @Test
    void createsWithSessionAuthorAndTrimmedContent() {
        allowAccess(ME);
        when(comments.save(DIARY, ME, "안녕하세요")).thenReturn(31L);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, ME, "안녕하세요", CREATED)));

        CommentResponse created = service.create(ME, GROUP, DIARY, new CommentRequest("  안녕하세요 \n"));

        assertEquals(new CommentResponse(31, DIARY, ME, "nick-9", "안녕하세요", CREATED, CREATED, true, true), created);
        verify(comments).save(DIARY, ME, "안녕하세요");
    }

    @Test
    void listsInRepositoryOrderWithPermissionsForTheViewer() {
        allowAccess(ME);
        when(comments.findAllByDiaryId(DIARY)).thenReturn(List.of(
                comment(1, FRIEND, "first", CREATED), comment(2, ME, "second", CREATED)));

        List<CommentResponse> listed = service.list(ME, GROUP, DIARY);

        assertEquals(List.of(1L, 2L), listed.stream().map(CommentResponse::id).toList());
        assertFalse(listed.get(0).canEdit());
        assertFalse(listed.get(0).canDelete());
        assertTrue(listed.get(1).canEdit());
        assertTrue(listed.get(1).canDelete());
    }

    @Test
    void emptyDiaryReturnsEmptyList() {
        allowAccess(ME);
        when(comments.findAllByDiaryId(DIARY)).thenReturn(List.of());
        assertEquals(List.of(), service.list(ME, GROUP, DIARY));
    }

    @Test
    void contentLengthBoundaryAcceptsOneAndOneThousandAndRejectsOneThousandOne() {
        assertEquals("a", CommentService.normalizeContent(new CommentRequest("a")));
        assertEquals(999, CommentService.normalizeContent(new CommentRequest("a".repeat(999))).length());
        assertEquals(1000, CommentService.normalizeContent(new CommentRequest("a".repeat(1000))).length());
        assertEquals(1000, CommentService.normalizeContent(new CommentRequest("  " + "a".repeat(1000) + "  ")).length());
        assertThrows(IllegalArgumentException.class,
                () -> CommentService.normalizeContent(new CommentRequest("a".repeat(1001))));
    }

    @Test
    void countsCharactersLikeMysqlCharLength() {
        String thousandEmoji = "😀".repeat(1000);
        assertEquals(thousandEmoji, CommentService.normalizeContent(new CommentRequest(thousandEmoji)));
        assertThrows(IllegalArgumentException.class,
                () -> CommentService.normalizeContent(new CommentRequest(thousandEmoji + "😀")));
    }

    @Test
    void rejectsMissingBlankAndNonStringContentBeforeSaving() {
        allowAccess(ME);
        for (CommentRequest bad : new CommentRequest[]{null, new CommentRequest(null), new CommentRequest(""),
                new CommentRequest("   \t\n "), new CommentRequest(123), new CommentRequest(true),
                new CommentRequest(List.of("x"))}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(ME, GROUP, DIARY, bad));
        }
        verify(comments, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    void refusesUnauthenticatedOrInvalidIdsBeforeRepositoryAccess() {
        assertThrows(LoginRequiredException.class, () -> service.list(null, GROUP, DIARY));
        assertThrows(LoginRequiredException.class, () -> service.create(0L, GROUP, DIARY, new CommentRequest("x")));
        assertThrows(IllegalArgumentException.class, () -> service.list(ME, 0, DIARY));
        assertThrows(IllegalArgumentException.class, () -> service.list(ME, GROUP, -1));
        verifyNoInteractions(comments, diaries, members);
    }

    @Test
    void rejectsNonmembersAndDiariesOutsideTheGroupAsNotFound() {
        assertThrows(DiaryNotFoundException.class, () -> service.list(ME, GROUP, DIARY));
        verifyNoInteractions(diaries);

        when(members.exists(GROUP, ME)).thenReturn(true);
        assertThrows(DiaryNotFoundException.class, () -> service.create(ME, GROUP, DIARY, new CommentRequest("x")));
        verify(diaries).findByIdAndGroupId(DIARY, GROUP);
        verifyNoInteractions(comments);
    }

    @Test
    void updatesOwnCommentAndReturnsLatestServerValues() {
        allowAccess(ME);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(
                Optional.of(comment(31, ME, "old", CREATED)),
                Optional.of(new Comment(31, DIARY, ME, "nick-9", "new", CREATED, EDITED)));
        when(comments.update(31, DIARY, ME, "new")).thenReturn(1);

        CommentResponse updated = service.update(ME, GROUP, DIARY, 31, new CommentRequest(" new "));

        assertEquals("new", updated.content());
        assertEquals(EDITED, updated.updatedAt());
        assertTrue(updated.canEdit());
    }

    @Test
    void anotherMembersCommentIsForbiddenEvenForTheGroupOwner() {
        allowAccess(ME); // ME may be the owner: ownership grants nothing over other people's comments.
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, FRIEND, "theirs", CREATED)));

        assertThrows(CommentForbiddenException.class,
                () -> service.update(ME, GROUP, DIARY, 31, new CommentRequest("mine now")));
        assertThrows(CommentForbiddenException.class, () -> service.delete(ME, GROUP, DIARY, 31));
        verify(comments, never()).update(anyLong(), anyLong(), anyLong(), any());
        verify(comments, never()).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    void commentOfAnotherDiaryOrMissingCommentIsNotFound() {
        allowAccess(ME);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> service.update(ME, GROUP, DIARY, 31, new CommentRequest("x")));
        assertThrows(CommentNotFoundException.class, () -> service.delete(ME, GROUP, DIARY, 31));
        assertThrows(IllegalArgumentException.class, () -> service.delete(ME, GROUP, DIARY, 0));
        verify(comments, never()).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    void invalidUpdateContentIsRejectedAfterOwnershipCheckAndNothingChanges() {
        allowAccess(ME);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, ME, "old", CREATED)));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(ME, GROUP, DIARY, 31, new CommentRequest("   ")));
        verify(comments, never()).update(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void writesThatChangeNoRowAreReportedAsNotFound() {
        allowAccess(ME);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, ME, "old", CREATED)));
        when(comments.update(31, DIARY, ME, "new")).thenReturn(0);
        when(comments.delete(31, DIARY, ME)).thenReturn(0);

        assertThrows(CommentNotFoundException.class,
                () -> service.update(ME, GROUP, DIARY, 31, new CommentRequest("new")));
        assertThrows(CommentNotFoundException.class, () -> service.delete(ME, GROUP, DIARY, 31));
    }

    @Test
    void deletesOnlyWithCommentDiaryAndAuthorScope() {
        allowAccess(ME);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, ME, "bye", CREATED)));
        when(comments.delete(31, DIARY, ME)).thenReturn(1);

        service.delete(ME, GROUP, DIARY, 31);

        verify(comments).delete(31, DIARY, ME);
    }

    @Test
    void leavingTheGroupBlocksAccessWithoutDeletingComments() {
        allowAccess(ME);
        when(comments.save(DIARY, ME, "before leaving")).thenReturn(31L);
        when(comments.findByIdAndDiaryId(31L, DIARY)).thenReturn(Optional.of(comment(31, ME, "before leaving", CREATED)));
        service.create(ME, GROUP, DIARY, new CommentRequest("before leaving"));

        when(members.exists(GROUP, ME)).thenReturn(false);
        assertThrows(DiaryNotFoundException.class, () -> service.list(ME, GROUP, DIARY));
        assertThrows(DiaryNotFoundException.class, () -> service.delete(ME, GROUP, DIARY, 31));
        verify(comments, never()).delete(anyLong(), anyLong(), anyLong());
    }

    private void allowAccess(long userId) {
        when(members.exists(GROUP, userId)).thenReturn(true);
        when(diaries.findByIdAndGroupId(DIARY, GROUP))
                .thenReturn(Optional.of(new Diary(DIARY, GROUP, FRIEND, "제목", "본문", CREATED, CREATED)));
    }

    private static Comment comment(long id, long authorId, String content, LocalDateTime at) {
        return new Comment(id, DIARY, authorId, "nick-" + authorId, content, at, at);
    }
}
