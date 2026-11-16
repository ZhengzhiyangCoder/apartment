package com.example.apartment.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class HttpIntegrationTest {
    protected static final String PASSWORD = "DemoPass2026!";
    protected static final String HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    @LocalServerPort protected int port;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected DataSource dataSource;
    @Autowired protected ObjectMapper json;

    @BeforeEach
    void prepareIsolatedFixtures() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("apartment_test");
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM hygiene_score_dimension");
        jdbc.update("DELETE FROM hygiene_score");
        jdbc.update("DELETE FROM hygiene_batch_room");
        jdbc.update("DELETE FROM hygiene_batch");
        jdbc.update("DELETE FROM hygiene_scheme_dimension");
        jdbc.update("DELETE FROM hygiene_scheme ORDER BY id DESC");
        jdbc.update("DELETE FROM repair_status_log");
        jdbc.update("DELETE FROM repair_image");
        jdbc.update("DELETE FROM repair_order");
        jdbc.update("DELETE FROM housing_residency");
        jdbc.update("DELETE FROM dorm_bed");
        jdbc.update("DELETE FROM dorm_room");
        jdbc.update("DELETE FROM dorm_building");
        jdbc.update("DELETE FROM student_profile");
        jdbc.update("DELETE FROM sys_user");
        for (String role : List.of("SYSTEM_ADMIN", "DORM_MANAGER", "STUDENT", "MAINTAINER")) {
            jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                    role.toLowerCase(), HASH, "模拟" + role, role);
        }
        jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,role,enabled) VALUES (?,?,?,?,false)",
                "disabled", HASH, "停用模拟账号", "STUDENT");
    }

    protected class Browser {
        public Browser() {}
        public final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        public HttpResponse<String> get(String path) throws Exception { return send(path, "GET", null, null, null); }
        public HttpResponse<String> login(String username, String password) throws Exception {
            return send("/api/auth/login", "POST", "application/x-www-form-urlencoded",
                    "username=" + username + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8), csrf());
        }
        public JsonNode account(String username) throws Exception {
            var response = get("/api/identity/accounts?q=" + username);
            assertThat(response.statusCode()).isEqualTo(200);
            return json.readTree(response.body()).path("items").get(0);
        }
        public HttpResponse<String> postJson(String path, Object body) throws Exception {
            return send(path, "POST", "application/json", json.writeValueAsString(body), csrf());
        }
        public String csrf() throws Exception {
            var response = get("/api/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return json.readTree(response.body()).path("token").asText();
        }
        public HttpResponse<String> send(String path, String method, String type, String body, String csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
            if (type != null) request.header("Content-Type", type);
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
