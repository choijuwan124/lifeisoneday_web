package com.example.project013982;

import com.example.project013982.auth.AuthController;
import com.example.project013982.auth.AuthService;
import com.example.project013982.common.GlobalExceptionHandler;
import com.example.project013982.user.User;
import com.example.project013982.user.UserRepository;
import com.example.project013982.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real authentication logic with an in-memory repository; MySQL is tested separately. */
class AuthSessionTests {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new AuthService(new UserService(new MemoryUsers()))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void signup() throws Exception {
        var response = mvc.perform(post("/api/auth/signup").contentType("application/json")
                .content("{\"username\":\"review\",\"password\":\"review-password\",\"nickname\":\"reviewer\"}"))
                .andReturn().getResponse();
        assertEquals(201, response.getStatus());
    }

    @Test
    void signupPasswordAuthenticatesAndLogoutInvalidatesSession() throws Exception {
        signup();
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"review\",\"password\":\"review-password\"}")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertEquals(1L, session.getAttribute("LOGIN_USER_ID"));
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"data\":{\"nickname\":\"reviewer\"}"));
        assertFalse(body.contains("password"));
        assertEquals(200, mvc.perform(post("/api/auth/logout").session(session)).andReturn().getResponse().getStatus());
        assertTrue(session.isInvalid());
    }

    @Test
    void successfulLoginRotatesAnExistingSessionId() throws Exception {
        signup();
        var session = new MockHttpSession();
        String oldId = session.getId();
        var result = mvc.perform(post("/api/auth/login").session(session).contentType("application/json")
                .content("{\"username\":\"review\",\"password\":\"review-password\"}")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertNotEquals(oldId, result.getRequest().getSession(false).getId());
        assertEquals(1L, result.getRequest().getSession(false).getAttribute("LOGIN_USER_ID"));
    }

    @Test
    void failedLoginDoesNotAuthenticateOrRotateSession() throws Exception {
        signup();
        var session = new MockHttpSession();
        String oldId = session.getId();
        var result = mvc.perform(post("/api/auth/login").session(session).contentType("application/json")
                .content("{\"username\":\"review\",\"password\":\"wrong\"}")).andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertNull(session.getAttribute("LOGIN_USER_ID"));
        assertEquals(oldId, session.getId());
    }

    @Test
    void anonymousLogoutSucceedsWithoutCreatingASession() throws Exception {
        var result = mvc.perform(post("/api/auth/logout")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void malformedLoginRequestDoesNotCreateASession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json").content("{"))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void anonymousSessionCheckDoesNotCreateASession() throws Exception {
        var result = mvc.perform(get("/api/auth/me")).andReturn();
        assertEquals(401, result.getResponse().getStatus());
        assertNull(result.getRequest().getSession(false));
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
    }

    @Test
    void sessionCheckReturnsOnlyPublicIdentityAndStopsAfterLogout() throws Exception {
        signup();
        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"review\",\"password\":\"review-password\"}")).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        var response = mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"id\":1"));
        assertTrue(body.contains("\"nickname\":\"reviewer\""));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("username"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        mvc.perform(post("/api/auth/logout").session(session));
        assertEquals(401, mvc.perform(get("/api/auth/me")).andReturn().getResponse().getStatus());
    }

    private static class MemoryUsers extends UserRepository {
        private final Map<String, User> users = new HashMap<>();
        MemoryUsers() { super(null); }
        @Override
        public boolean existsByUsername(String username) { return users.containsKey(username); }
        @Override
        public Optional<User> findByUsername(String username) { return Optional.ofNullable(users.get(username)); }
        @Override
        public User save(String username, String hash, String nickname) {
            assertNotEquals("review-password", hash);
            User user = new User(1L, username, hash, nickname);
            users.put(username, user);
            return user;
        }
    }
}
