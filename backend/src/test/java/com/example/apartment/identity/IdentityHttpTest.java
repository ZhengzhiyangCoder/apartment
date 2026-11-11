package com.example.apartment.identity;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityHttpTest {
    private static final String PASSWORD = "DemoPass2026!";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;

    @BeforeEach
    void prepareIsolatedFixtures() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("apartment_test");
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        jdbc.update("DELETE FROM sys_user");
        for (String role : List.of("SYSTEM_ADMIN", "DORM_MANAGER", "STUDENT", "MAINTAINER")) {
            jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                    role.toLowerCase(), HASH, "模拟" + role, role);
        }
        jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,role,enabled) VALUES (?,?,?,?,false)",
                "disabled", HASH, "停用模拟账号", "STUDENT");
    }

    @Test
    void fourRolesCanLoginAndReadOnlyTheirOwnProfile() throws Exception {
        for (String role : List.of("SYSTEM_ADMIN", "DORM_MANAGER", "STUDENT", "MAINTAINER")) {
            var browser = new Browser();
            assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
            assertThat(browser.login(role.toLowerCase(), PASSWORD).statusCode()).isEqualTo(200);
            var me = browser.get("/api/auth/me");
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(json.readTree(me.body()).path("role").asText()).isEqualTo(role);
            assertThat(json.readTree(me.body()).path("username").asText()).isEqualTo(role.toLowerCase());
            assertThat(me.body()).doesNotContain("password", "hash");
        }
    }

    @Test
    void roleWorkspaceRejectsOtherRolesAndForgedIdentityParameters() throws Exception {
        var browser = new Browser();
        browser.login("student", PASSWORD);
        assertThat(browser.get("/api/identity/workspaces/STUDENT").statusCode()).isEqualTo(200);
        for (String role : List.of("SYSTEM_ADMIN", "DORM_MANAGER", "MAINTAINER")) {
            assertThat(browser.get("/api/identity/workspaces/" + role).statusCode()).isEqualTo(403);
        }
        var me = json.readTree(browser.get("/api/auth/me?username=system_admin&role=SYSTEM_ADMIN").body());
        assertThat(me.path("username").asText()).isEqualTo("student");
    }

    @Test
    void logoutInvalidatesTheOldCookieAndEveryWriteRequiresCsrf() throws Exception {
        var browser = new Browser();
        assertThat(browser.send("/api/auth/login", "POST", "application/x-www-form-urlencoded",
                "username=student&password=" + PASSWORD, null).statusCode()).isEqualTo(403);
        browser.login("student", PASSWORD);
        String oldCookie = browser.cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("APARTMENTSESSION")).findFirst().orElseThrow().getValue();
        assertThat(browser.send("/api/auth/logout", "POST", null, null, null).statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(browser.send("/api/auth/logout", "POST", null, null, browser.csrf()).statusCode()).isEqualTo(204);
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                .header("Cookie", "APARTMENTSESSION=" + oldCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    void invalidAndDisabledAccountsReturnSafeErrors() throws Exception {
        for (String account : List.of("unknown", "disabled", "student")) {
            var response = new Browser().login(account, account.equals("student") ? "incorrect" : PASSWORD);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).doesNotContain("stackTrace", "password_hash", "SELECT", HASH);
            assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        }
    }

    @Test
    void passwordChangeValidatesCurrentPasswordPersistsAndRevokesSessions() throws Exception {
        var browser = new Browser();
        var otherSession = new Browser();
        browser.login("student", PASSWORD);
        otherSession.login("student", PASSWORD);
        String token = browser.csrf();
        for (String body : List.of(
                "{\"currentPassword\":\"wrong\",\"newPassword\":\"UpdatedPass2026!\"}",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + "密".repeat(25) + "\"}")) {
            assertThat(browser.send("/api/auth/password", "POST", "application/json", body, token).statusCode()).isEqualTo(400);
        }
        assertThat(new Browser().login("student", PASSWORD).statusCode()).isEqualTo(200);
        String valid = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"UpdatedPass2026!\"}";
        assertThat(browser.send("/api/auth/password", "POST", "application/json", valid, null).statusCode()).isEqualTo(403);
        assertThat(browser.send("/api/auth/password", "POST", "application/json", valid, token).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(otherSession.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(new Browser().login("student", PASSWORD).statusCode()).isEqualTo(401);
        var fresh = new Browser();
        assertThat(fresh.login("student", "UpdatedPass2026!").statusCode()).isEqualTo(200);
        assertThat(json.readTree(fresh.get("/api/auth/me").body()).path("username").asText()).isEqualTo("student");
    }

    @Test
    void loginRotatesSessionAndCsrfAndSetsHttpOnlyCookie() throws Exception {
        var browser = new Browser();
        String token = browser.csrf();
        String previousId = browser.cookies.getCookieStore().getCookies().get(0).getValue();
        var login = browser.login("student", PASSWORD);
        assertThat(login.headers().allValues("Set-Cookie").toString()).contains("HttpOnly", "SameSite=Strict", "Path=/api");
        assertThat(browser.cookies.getCookieStore().getCookies().get(0).getValue()).isNotEqualTo(previousId);
        assertThat(browser.send("/api/auth/logout", "POST", null, null, token).statusCode()).isEqualTo(403);
    }

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return send(path, "GET", null, null, null); }
        HttpResponse<String> login(String username, String password) throws Exception {
            return send("/api/auth/login", "POST", "application/x-www-form-urlencoded",
                    "username=" + username + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8), csrf());
        }
        String csrf() throws Exception {
            var response = get("/api/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return json.readTree(response.body()).path("token").asText();
        }
        HttpResponse<String> send(String path, String method, String type, String body, String csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
            if (type != null) request.header("Content-Type", type);
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
