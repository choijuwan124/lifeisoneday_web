package com.example.project013982.like;

import com.example.project013982.common.GlobalExceptionHandler;
import com.example.project013982.diary.Diary;
import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.member.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LikeControllerTests {
    private static final String PATH = "/api/groups/4/diaries/7/likes";
    private final LikeRepository likes = mock(LikeRepository.class);
    private final DiaryRepository diaries = mock(DiaryRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final MockHttpSession session = new MockHttpSession();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new LikeController(new LikeService(likes, diaries, members)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        session.setAttribute("LOGIN_USER_ID", 9L);
    }

    @Test
    void mapsGetPutAndDeleteAndUsesOnlySessionIdentity() throws Exception {
        when(members.exists(4L, 9L)).thenReturn(true);
        when(diaries.findByIdAndGroupId(7L, 4L))
                .thenReturn(Optional.of(new Diary(7L, 4L, 9L, "제목", "본문", null, null)));
        when(likes.status(7, 9)).thenReturn(new LikeStatus(7, 2, false),
                new LikeStatus(7, 3, true), new LikeStatus(7, 2, false));

        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.diaryId").value(7))
                .andExpect(jsonPath("$.data.likeCount").value(2))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
        mvc.perform(put(PATH + "/me").param("userId", "99").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(3))
                .andExpect(jsonPath("$.data.likedByMe").value(true));
        mvc.perform(delete(PATH + "/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(2))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
        verify(likes).add(7, 9);
        verify(likes).remove(7, 9);
    }

    @Test
    void everyEndpointRequiresLoginWithoutCreatingSession() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get(PATH), put(PATH + "/me"), delete(PATH + "/me")}) {
            var result = mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false)).andReturn();
            assertNull(result.getRequest().getSession(false));
        }
        verifyNoInteractions(likes, members, diaries);
    }

    @Test
    void invalidFormatOverflowAndNonpositiveIdsReturnBadRequest() throws Exception {
        mvc.perform(get("/api/groups/no/diaries/7/likes").session(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/groups/4/diaries/9223372036854775808/likes/me").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/groups/4/diaries/0/likes/me").session(session))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(likes, members, diaries);
    }

    @Test
    void missingMembershipOrDiaryReturnsNotFound() throws Exception {
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        when(members.exists(4L, 9L)).thenReturn(true);
        mvc.perform(put(PATH + "/me").session(session)).andExpect(status().isNotFound());
        verifyNoInteractions(likes);
    }
}
