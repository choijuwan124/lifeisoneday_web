package com.example.project013982.like;

import com.example.project013982.diary.Diary;
import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import com.example.project013982.member.GroupMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LikeServiceTests {
    private final LikeRepository likes = mock(LikeRepository.class);
    private final DiaryRepository diaries = mock(DiaryRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final LikeService service = new LikeService(likes, diaries, members);

    @Test
    void readsStatusAfterEveryChangeIncludingRepeatedRequests() {
        allowAccess();
        LikeStatus initial = new LikeStatus(7, 2, false);
        LikeStatus liked = new LikeStatus(7, 3, true);
        when(likes.status(7, 9)).thenReturn(initial, liked, liked, initial, initial);

        assertEquals(initial, service.get(9L, 4, 7));
        assertEquals(liked, service.add(9L, 4, 7));
        assertEquals(liked, service.add(9L, 4, 7));
        assertEquals(initial, service.remove(9L, 4, 7));
        assertEquals(initial, service.remove(9L, 4, 7));

        var order = inOrder(likes);
        order.verify(likes).status(7, 9);
        order.verify(likes).add(7, 9);
        order.verify(likes).status(7, 9);
        order.verify(likes).add(7, 9);
        order.verify(likes).status(7, 9);
        order.verify(likes).remove(7, 9);
        order.verify(likes).status(7, 9);
        order.verify(likes).remove(7, 9);
        order.verify(likes).status(7, 9);
    }

    @Test
    void refusesUnauthenticatedOrInvalidRequestsBeforeRepositoryAccess() {
        assertThrows(LoginRequiredException.class, () -> service.get(null, 4, 7));
        assertThrows(IllegalArgumentException.class, () -> service.add(9L, 0, 7));
        assertThrows(IllegalArgumentException.class, () -> service.remove(9L, 4, -1));
        verifyNoInteractions(likes, diaries, members);
    }

    @Test
    void rejectsNonmembersAndDiariesOutsideTheGroup() {
        assertThrows(DiaryNotFoundException.class, () -> service.get(9L, 4, 7));
        verifyNoInteractions(diaries);

        when(members.exists(4L, 9L)).thenReturn(true);
        assertThrows(DiaryNotFoundException.class, () -> service.add(9L, 4, 7));
        verify(diaries).findByIdAndGroupId(7L, 4L);
        verifyNoInteractions(likes);
    }

    @Test
    void leavingTheGroupBlocksFurtherAccessWithoutDeletingLikes() {
        allowAccess();
        when(likes.status(7, 9)).thenReturn(new LikeStatus(7, 1, true));
        assertEquals(new LikeStatus(7, 1, true), service.add(9L, 4, 7));

        when(members.exists(4L, 9L)).thenReturn(false);
        assertThrows(DiaryNotFoundException.class, () -> service.get(9L, 4, 7));
        assertThrows(DiaryNotFoundException.class, () -> service.remove(9L, 4, 7));
        verify(likes).add(7, 9);
        verify(likes).status(7, 9);
        verifyNoMoreInteractions(likes);
    }

    private void allowAccess() {
        when(members.exists(4L, 9L)).thenReturn(true);
        when(diaries.findByIdAndGroupId(7L, 4L))
                .thenReturn(Optional.of(new Diary(7L, 4L, 9L, "제목", "본문", null, null)));
    }
}
