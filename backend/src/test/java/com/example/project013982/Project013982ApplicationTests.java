package com.example.project013982;

import com.example.project013982.auth.AuthController;
import com.example.project013982.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
// These tests use a JDBC double and need no agent-based Mockito reset listener.
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
@Import(Project013982ApplicationTests.JdbcTestConfiguration.class)
class Project013982ApplicationTests {
    // The existing test profile disables DataSource auto-configuration.
    // Supply JDBC only for tests; keep the real signup services and repository.
    @TestConfiguration(proxyBeanMethods = false)
    static class JdbcTestConfiguration {
        @Bean
        TestJdbcTemplate testJdbcTemplate() {
            return new TestJdbcTemplate();
        }
    }

    static class TestJdbcTemplate extends JdbcTemplate {
        boolean exists;
        boolean duplicateOnInsert;
        boolean unavailable;
        int reads;
        int writes;
        String lastUsername;

        TestJdbcTemplate() {
            super();
        }

        @Override
        public void afterPropertiesSet() {
            // This test double has no DataSource and never opens a connection.
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            reads++;
            lastUsername = (String) args[0];
            if (unavailable) throw new IllegalStateException("private connection details");
            return requiredType.cast(exists ? 1L : 0L);
        }

        @Override
        public int update(PreparedStatementCreator creator, KeyHolder keys) {
            if (duplicateOnInsert) throw new DuplicateKeyException("private constraint details");
            writes++;
            keys.getKeyList().add(Map.of("id", 1L));
            return 1;
        }
    }

    @Autowired
    private TestJdbcTemplate jdbc;
    @Autowired
    private AuthController authController;
    @Autowired
    private GlobalExceptionHandler exceptionHandler;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.exists = false;
        jdbc.duplicateOnInsert = false;
        jdbc.unavailable = false;
        jdbc.reads = 0;
        jdbc.writes = 0;
        jdbc.lastUsername = null;
        mvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(exceptionHandler).build();
    }

    @Test
    void contextLoads() {
        assertNotNull(authController);
    }

    @Test
    void signupReturnsCreatedWithoutExposingPassword() throws Exception {
        String body = signup("{\"username\":\" alice \",\"password\":\" secret \",\"nickname\":\"친구\"}", 201);
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("로그인"));
        assertTrue(body.contains("\"data\":null"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("pbkdf2"));
        assertEquals("alice", jdbc.lastUsername);
        assertEquals(1, jdbc.writes);
    }

    @Test
    void signupRejectsDuplicateUsername() throws Exception {
        jdbc.exists = true;
        String body = signup(validSignup(), 400);
        assertTrue(body.contains("이미 사용 중"));
        assertTrue(body.contains("\"success\":false"));
        assertEquals(0, jdbc.writes);
    }

    @Test
    void signupRejectsMissingAndBlankFieldsBeforeDatabaseAccess() throws Exception {
        for (String json : new String[]{"{}", "null", "",
                "{\"username\":\" \",\"password\":\"secret\",\"nickname\":\"친구\"}",
                "{\"username\":\"alice\",\"password\":\" \",\"nickname\":\"친구\"}",
                "{\"username\":\"alice\",\"password\":\"secret\"}"}) {
            assertTrue(signup(json, 400).contains("\"success\":false"));
        }
        assertEquals(0, jdbc.reads);
        assertEquals(0, jdbc.writes);
    }

    @Test
    void signupRejectsMalformedJson() throws Exception {
        assertTrue(signup("{", 400).contains("올바른 JSON"));
        assertEquals(0, jdbc.reads);
    }

    @Test
    void concurrentDuplicateSignupReturnsFailure() throws Exception {
        jdbc.duplicateOnInsert = true;
        String body = signup(validSignup(), 400);
        assertTrue(body.contains("이미 사용 중"));
        assertFalse(body.contains("private"));
    }

    @Test
    void databaseFailureDoesNotExposeInternalDetails() throws Exception {
        jdbc.unavailable = true;
        String body = signup(validSignup(), 500);
        assertTrue(body.contains("서버 오류"));
        assertFalse(body.contains("private"));
    }

    private static String validSignup() {
        return "{\"username\":\"alice\",\"password\":\"secret\",\"nickname\":\"친구\"}";
    }

    private String signup(String json, int expectedStatus) throws Exception {
        var response = mvc.perform(post("/api/auth/signup")
                        .contentType("application/json").content(json))
                .andReturn().getResponse();
        assertEquals(expectedStatus, response.getStatus(), "Unexpected signup HTTP status");
        return response.getContentAsString(StandardCharsets.UTF_8);
    }
}
