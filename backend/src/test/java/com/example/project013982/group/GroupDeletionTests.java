package com.example.project013982.group;

import com.example.project013982.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

class GroupDeletionTests {
    private GroupRepository repository;
    private MockMvc mvc;

    @BeforeEach void setup() {
        repository = mock(GroupRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new GroupController(new GroupService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private MockHttpSession session(long id) {
        var session = new MockHttpSession();
        session.setAttribute("LOGIN_USER_ID", id);
        return session;
    }

    private DiaryGroup group() {
        return new DiaryGroup(10L, "친구 일기장", null, 1L, LocalDateTime.now());
    }

    @Test void anonymousCannotDelete() throws Exception {
        assertEquals(401, mvc.perform(delete("/api/groups/10")).andReturn().getResponse().getStatus());
        verifyNoInteractions(repository);
    }

    @Test void outsiderOrMissingGroupCannotDelete() throws Exception {
        when(repository.findByIdAndMemberIdForUpdate(10L, 3L)).thenReturn(Optional.empty());
        assertEquals(404, mvc.perform(delete("/api/groups/10").session(session(3))).andReturn().getResponse().getStatus());
        verify(repository, never()).deleteDiaries(any());
        verify(repository, never()).deleteOwnedGroup(any(), any());
    }

    @Test void memberCannotDeleteOwnerGroupOrItsDiaries() throws Exception {
        when(repository.findByIdAndMemberIdForUpdate(10L, 2L)).thenReturn(Optional.of(group()));
        assertEquals(403, mvc.perform(delete("/api/groups/10").session(session(2))).andReturn().getResponse().getStatus());
        verify(repository, never()).deleteDiaries(any());
        verify(repository, never()).deleteOwnedGroup(any(), any());
    }

    @Test void ownerDeletesOnlyRequestedGroupAfterItsDiaries() throws Exception {
        when(repository.findByIdAndMemberIdForUpdate(10L, 1L)).thenReturn(Optional.of(group()));
        when(repository.deleteOwnedGroup(10L, 1L)).thenReturn(1);
        assertEquals(200, mvc.perform(delete("/api/groups/10").session(session(1))).andReturn().getResponse().getStatus());
        var order = inOrder(repository);
        order.verify(repository).findByIdAndMemberIdForUpdate(10L, 1L);
        order.verify(repository).deleteDiaries(10L);
        order.verify(repository).deleteOwnedGroup(10L, 1L);
        order.verifyNoMoreInteractions();
    }

    @Test void invalidIdsAreRejectedBeforeAnyDatabaseWrite() throws Exception {
        for (String id : new String[]{"0", "-1", "invalid", "99999999999999999999999"}) {
            assertEquals(400, mvc.perform(delete("/api/groups/" + id).session(session(1))).andReturn().getResponse().getStatus());
        }
        verifyNoInteractions(repository);
    }

    @Test void failedDeletionDoesNotReportSuccess() throws Exception {
        when(repository.findByIdAndMemberIdForUpdate(10L, 1L)).thenReturn(Optional.of(group()));
        when(repository.deleteOwnedGroup(10L, 1L)).thenReturn(0);
        assertEquals(404, mvc.perform(delete("/api/groups/10").session(session(1))).andReturn().getResponse().getStatus());
    }
}
