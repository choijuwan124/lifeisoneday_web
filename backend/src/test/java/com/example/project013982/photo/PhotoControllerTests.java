package com.example.project013982.photo;

import com.example.project013982.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhotoControllerTests {
    private static final String PATH = "/api/groups/4/diaries/7/photo";
    private final PhotoService service = mock(PhotoService.class);
    private final MockHttpSession session = new MockHttpSession();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PhotoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        session.setAttribute("LOGIN_USER_ID", 9L);
    }

    @Test
    void metadataCanReturnNullAndPublicPathDoesNotExposeStorageKey() throws Exception {
        when(service.getMetadata(9L, 4L, 7L)).thenReturn(Optional.empty());
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").value(nullValue()));

        when(service.getMetadata(9L, 4L, 7L)).thenReturn(Optional.of(photo()));
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.diaryId").value(7))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(23))
                .andExpect(jsonPath("$.data.contentPath").value(PATH + "/content"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    void uploadUsesFilePartAndDeleteReturnsNullData() throws Exception {
        when(service.replace(eq(9L), eq(4L), eq(7L), any())).thenReturn(photo());
        MockMultipartFile file = new MockMultipartFile("file", "original.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart(PATH).file(file).session(session).with(request -> {
                    request.setMethod("PUT"); return request;
                }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentPath").value(PATH + "/content"));

        mvc.perform(delete(PATH).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").value(nullValue()));
        verify(service).delete(9L, 4L, 7L);
    }

    @Test
    void contentIsRawImageWithPrivateNoCacheHeaders() throws Exception {
        when(service.getContent(9L, 4L, 7L)).thenReturn(new PhotoService.PhotoContent("image/jpeg", new byte[]{5, 6, 7}));
        mvc.perform(get(PATH + "/content").session(session))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{5, 6, 7}))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void mapsLoginMissingContentAndInvalidIds() throws Exception {
        when(service.getMetadata(null, 4L, 7L)).thenThrow(new PhotoService.LoginRequiredException());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        when(service.getContent(9L, 4L, 7L)).thenThrow(new PhotoService.PhotoNotFoundException());
        mvc.perform(get(PATH + "/content").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/groups/0/diaries/7/photo").session(session)).andExpect(status().isBadRequest());
    }

    private static PhotoResponse photo() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 28, 9, 0);
        return new PhotoResponse(2L, 7L, "image/png", 23L, at, at, PATH + "/content");
    }
}
