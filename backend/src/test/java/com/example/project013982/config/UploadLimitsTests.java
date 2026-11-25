package com.example.project013982.config;

import com.example.project013982.common.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP multipart parser tests. This probe is test-only, not a photo/auth API. */
class UploadLimitsTests {
    private static final int MAX_FILE = 5 * 1024 * 1024;
    private static ConfigurableApplicationContext context;
    private static int port;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({UploadProbe.class, GlobalExceptionHandler.class})
    static class TestApp { }

    @RestController
    static class UploadProbe {
        @PutMapping("/__test/upload")
        Map<String, Long> upload(@RequestPart("file") MultipartFile file) {
            return Map.of("size", file.getSize());
        }
    }

    @BeforeAll static void startServer() {
        String config = Path.of("src/main/resources/application.properties").toAbsolutePath().toUri().toString();
        context = new SpringApplicationBuilder(TestApp.class).run(
                "--spring.config.location=" + config,
                "--spring.config.import=",
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "--server.port=0");
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll static void stopServer() {
        if (context != null) context.close();
    }

    private HttpResponse<String> upload(int size) throws Exception {
        String boundary = "liod-test-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[size]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__test/upload"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void acceptsFiveMiBFileIncludingMultipartEnvelope() throws Exception {
        var response = upload(MAX_FILE);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("5242880"));
    }

    @Test void rejectsOneByteOverFileLimitWithApiResponse() throws Exception {
        var response = upload(MAX_FILE + 1);
        assertEquals(413, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"success\":false"));
        assertTrue(response.body().contains("5MB"));
    }

    @Test void acceptsSmallMultipartFile() throws Exception {
        var response = upload(64);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("64"));
    }
}
