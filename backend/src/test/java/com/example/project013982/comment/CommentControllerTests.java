package com.example.project013982.comment;

import com.example.project013982.common.GlobalExceptionHandler;
import com.example.project013982.diary.Diary;
import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.member.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommentControllerTests {
    private static final String PATH = "/api/groups/4/diaries/7/comments";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 27, 10, 0, 0, 123_000_000);

    private final CommentRepository comments = mock(CommentRepository.class);
    private final DiaryRepository diaries = mock(DiaryRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final MockHttpSession session = new MockHttpSession();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CommentController(new CommentService(comments, diaries, members)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        session.setAttribute("LOGIN_USER_ID", 9L);
    }

    @Test
    void listsCommentsInTheCommonResponseShape() throws Exception {
        allowAccess();
        when(comments.findAllByDiaryId(7)).thenReturn(List.of(
                new Comment(1, 7, 10, "friend", "first", AT, AT), new Comment(2, 7, 9, "me", "second", AT, AT)));

        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].diaryId").value(7))
                .andExpect(jsonPath("$.data[0].authorId").value(10))
                .andExpect(jsonPath("$.data[0].authorNickname").value("friend"))
                .andExpect(jsonPath("$.data[0].content").value("first"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-27T10:00:00.123"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-11-27T10:00:00.123"))
                .andExpect(jsonPath("$.data[0].canEdit").value(false))
                .andExpect(jsonPath("$.data[0].canDelete").value(false))
                .andExpect(jsonPath("$.data[1].canEdit").value(true))
                .andExpect(jsonPath("$.data[1].canDelete").value(true));
    }

    @Test
    void emptyListIsAnEmptyArray() throws Exception {
        allowAccess();
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void createReturns201AndIgnoresAuthorIdInTheBody() throws Exception {
        allowAccess();
        when(comments.save(7, 9, "hello")).thenReturn(31L);
        when(comments.findByIdAndDiaryId(31, 7)).thenReturn(Optional.of(new Comment(31, 7, 9, "me", "hello", AT, AT)));

        mvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  hello  \",\"authorId\":99,\"userId\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(31))
                .andExpect(jsonPath("$.data.authorId").value(9))
                .andExpect(jsonPath("$.data.content").value("hello"));
        verify(comments).save(7, 9, "hello");
    }

    @Test
    void updateReturnsTheLatestCommentAndDeleteReturnsNullData() throws Exception {
        allowAccess();
        LocalDateTime edited = AT.plusMinutes(5);
        when(comments.findByIdAndDiaryId(31, 7)).thenReturn(
                Optional.of(new Comment(31, 7, 9, "me", "old", AT, AT)),
                Optional.of(new Comment(31, 7, 9, "me", "new", AT, edited)),
                Optional.of(new Comment(31, 7, 9, "me", "new", AT, edited)));
        when(comments.update(31, 7, 9, "new")).thenReturn(1);
        when(comments.delete(31, 7, 9)).thenReturn(1);

        mvc.perform(put(PATH + "/31").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("new"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-27T10:05:00.123"));
        mvc.perform(delete(PATH + "/31").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
        verify(comments).delete(31, 7, 9);
    }

    @Test
    void everyEndpointRequiresLoginWithoutCreatingSession() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                get(PATH), post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"),
                put(PATH + "/31").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"),
                delete(PATH + "/31")}) {
            var result = mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false)).andReturn();
            assertNull(result.getRequest().getSession(false));
        }
        verifyNoInteractions(comments, members, diaries);
    }

    @Test
    void invalidIdsReturnBadRequestBeforeAnyLookup() throws Exception {
        mvc.perform(get("/api/groups/no/diaries/7/comments").session(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/groups/4/diaries/9223372036854775808/comments").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/groups/4/diaries/0/comments").session(session)).andExpect(status().isBadRequest());
        mvc.perform(delete(PATH + "/abc").session(session)).andExpect(status().isBadRequest());
        verifyNoInteractions(comments, members, diaries);
    }

    @Test
    void invalidBodiesReturnBadRequestWithoutSaving() throws Exception {
        allowAccess();
        for (String body : new String[]{"{}", "{\"content\":null}", "{\"content\":\"\"}", "{\"content\":\"   \"}",
                "{\"content\":123}", "{\"content\":\"" + "a".repeat(1001) + "\"}", "{bad json"}) {
            mvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        mvc.perform(post(PATH).session(session)).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
        verify(comments, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    void thousandCharactersAreAccepted() throws Exception {
        allowAccess();
        String content = "a".repeat(1000);
        when(comments.save(7, 9, content)).thenReturn(31L);
        when(comments.findByIdAndDiaryId(31, 7)).thenReturn(Optional.of(new Comment(31, 7, 9, "me", content, AT, AT)));
        mvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void nonmemberMissingDiaryAndForeignCommentReturnNotFound() throws Exception {
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        when(members.exists(4L, 9L)).thenReturn(true);
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());

        allowAccess();
        mvc.perform(put(PATH + "/55").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/55").session(session)).andExpect(status().isNotFound());
        verify(comments, never()).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    void anotherAuthorsCommentReturnsForbidden() throws Exception {
        allowAccess();
        when(comments.findByIdAndDiaryId(31, 7)).thenReturn(Optional.of(new Comment(31, 7, 10, "friend", "x", AT, AT)));
        mvc.perform(put(PATH + "/31").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"mine\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(delete(PATH + "/31").session(session)).andExpect(status().isForbidden());
        verify(comments, never()).update(anyLong(), anyLong(), anyLong(), any());
        verify(comments, never()).delete(anyLong(), anyLong(), anyLong());
    }

    private void allowAccess() {
        when(members.exists(4L, 9L)).thenReturn(true);
        when(diaries.findByIdAndGroupId(7L, 4L))
                .thenReturn(Optional.of(new Diary(7L, 4L, 10L, "제목", "본문", AT, AT)));
    }
}
