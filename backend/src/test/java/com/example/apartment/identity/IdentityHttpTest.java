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
        jdbc.update("DELETE FROM operation_log");
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

    @Test
    void administratorCreatesSearchesAndPagesAccountsThatCanActuallyLogin() throws Exception {
        var admin = new Browser();
        admin.login("system_admin", PASSWORD);
        var created = admin.postJson("/api/identity/accounts", java.util.Map.of(
                "username", "new_student", "displayName", "新增模拟学生", "role", "STUDENT", "password", "CreatedPass2026!"));
        assertThat(created.statusCode()).isEqualTo(201);
        var account = json.readTree(created.body());
        assertThat(account.path("id").asLong()).isPositive();
        assertThat(account.path("enabled").asBoolean()).isTrue();
        assertThat(created.body()).doesNotContain("password", "hash", "CreatedPass");
        var results = json.readTree(admin.get("/api/identity/accounts?q=new_student&role=STUDENT&enabled=true&page=1&size=1").body());
        assertThat(results.path("total").asInt()).isEqualTo(1);
        assertThat(results.path("items").get(0).path("id")).isEqualTo(account.path("id"));
        assertThat(json.readTree(admin.get("/api/identity/accounts?page=2&size=1").body()).path("items").size()).isEqualTo(1);
        assertThat(new Browser().login("new_student", "CreatedPass2026!").statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/identity/accounts", java.util.Map.of(
                "username", "new_student", "displayName", "重复", "role", "STUDENT", "password", PASSWORD)).statusCode()).isEqualTo(409);
    }

    @Test
    void roleResetAndStateChangesRevokeOldSessionsAndKeepAuditedIdentity() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        var student = new Browser(); student.login("student", PASSWORD);
        var initial = admin.account("student");
        long id = initial.path("id").asLong();
        String base = "/api/identity/accounts/" + id;
        assertThat(admin.postJson(base + "/role", java.util.Map.of("role", "MAINTAINER", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(student.get("/api/identity/workspaces/STUDENT").statusCode()).isEqualTo(401);
        assertThat(student.login("student", PASSWORD).statusCode()).isEqualTo(200);
        assertThat(student.get("/api/identity/workspaces/STUDENT").statusCode()).isEqualTo(403);
        assertThat(student.get("/api/identity/workspaces/MAINTAINER").statusCode()).isEqualTo(200);
        assertThat(admin.postJson(base + "/reset-password", java.util.Map.of("newPassword", "ResetPass2026!", "version", 1)).statusCode()).isEqualTo(200);
        assertThat(student.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(new Browser().login("student", PASSWORD).statusCode()).isEqualTo(401);
        assertThat(student.login("student", "ResetPass2026!").statusCode()).isEqualTo(200);
        assertThat(admin.postJson(base + "/enabled", java.util.Map.of("enabled", false, "version", 2)).statusCode()).isEqualTo(200);
        assertThat(student.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(new Browser().login("student", "ResetPass2026!").statusCode()).isEqualTo(401);
        assertThat(admin.account("student").path("id").asLong()).isEqualTo(id);
        assertThat(admin.account("student").path("enabled").asBoolean()).isFalse();
        assertThat(admin.postJson(base + "/enabled", java.util.Map.of("enabled", true, "version", 3)).statusCode()).isEqualTo(200);
        assertThat(student.login("student", "ResetPass2026!").statusCode()).isEqualTo(200);
        // Durable audit is an explicit requirement; verify its storage contract, not private service calls.
        var logs = jdbc.queryForList("SELECT actor_id, action, before_data, after_data, created_at FROM operation_log WHERE target_id = ? ORDER BY id", id);
        assertThat(logs).hasSize(4);
        assertThat(logs.stream().map(row -> row.get("action"))).containsExactly(
                "ACCOUNT_ROLE_CHANGED", "ACCOUNT_PASSWORD_RESET", "ACCOUNT_DISABLED", "ACCOUNT_ENABLED");
        assertThat(logs.toString()).doesNotContain(PASSWORD, "ResetPass2026!", "passwordHash", "password_hash", "$2a$");
        assertThat(json.readTree(logs.get(0).get("before_data").toString()).path("role").asText()).isEqualTo("STUDENT");
        assertThat(json.readTree(logs.get(0).get("after_data").toString()).path("role").asText()).isEqualTo("MAINTAINER");
        for (var log : logs) {
            assertThat(log.get("actor_id")).isEqualTo(admin.account("system_admin").path("id").asLong());
            assertThat(log.get("created_at")).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class, id)).startsWith("$2a$12$");
    }

    @Test
    void accountAdministrationRejectsAnonymousOtherRolesForgedIdsAndMissingCsrf() throws Exception {
        var anonymous = new Browser();
        assertThat(anonymous.get("/api/identity/accounts").statusCode()).isEqualTo(401);
        var body = java.util.Map.of("username", "forged_user", "displayName", "越权测试", "role", "SYSTEM_ADMIN", "password", PASSWORD, "actorId", 1);
        assertThat(anonymous.postJson("/api/identity/accounts", body).statusCode()).isEqualTo(401);
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        long target = admin.account("student").path("id").asLong();
        for (String role : List.of("dorm_manager", "student", "maintainer")) {
            var user = new Browser(); user.login(role, PASSWORD);
            assertThat(user.get("/api/identity/accounts?role=SYSTEM_ADMIN").statusCode()).isEqualTo(403);
            assertThat(user.postJson("/api/identity/accounts", body).statusCode()).isEqualTo(403);
            for (String action : List.of("role", "reset-password", "enabled")) {
                assertThat(user.postJson("/api/identity/accounts/" + target + "/" + action,
                        java.util.Map.of("role", "SYSTEM_ADMIN", "enabled", false, "newPassword", PASSWORD, "version", 0, "actorId", 1)).statusCode()).isEqualTo(403);
            }
        }
        assertThat(admin.send("/api/identity/accounts", "POST", "application/json", json.writeValueAsString(body), null).statusCode()).isEqualTo(403);
        assertThat(admin.send("/api/identity/accounts/" + target, "DELETE", null, null, admin.csrf()).statusCode()).isEqualTo(403);
        assertThat(admin.account("student").path("enabled").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Integer.class)).isZero();
    }

    @Test
    void invalidQueriesInputsAndStaleVersionsHaveSafeRecoverableErrors() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        for (String query : List.of("size=101", "size=0", "page=0", "page=2147483648", "role=ROOT", "enabled=invalid", "q=" + "a".repeat(65))) {
            assertThat(admin.get("/api/identity/accounts?" + query).statusCode()).as(query).isEqualTo(400);
        }
        for (String password : List.of("short", "密".repeat(25))) {
            assertThat(admin.postJson("/api/identity/accounts", java.util.Map.of("username", "invalid", "displayName", "测试", "role", "STUDENT", "password", password)).statusCode()).isEqualTo(400);
        }
        assertThat(admin.postJson("/api/identity/accounts", java.util.Map.of("username", "bad username", "displayName", "测试", "role", "STUDENT", "password", PASSWORD)).statusCode()).isEqualTo(400);
        long id = admin.account("student").path("id").asLong();
        String base = "/api/identity/accounts/" + id;
        assertThat(admin.postJson(base + "/role", java.util.Map.of("role", "MAINTAINER")).statusCode()).isEqualTo(400);
        assertThat(admin.postJson(base + "/role", java.util.Map.of("role", "ROOT", "version", 0)).statusCode()).isEqualTo(400);
        assertThat(admin.postJson("/api/identity/accounts/9223372036854775807/enabled", java.util.Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(404);
        assertThat(admin.postJson(base + "/role", java.util.Map.of("role", "MAINTAINER", "version", 0)).statusCode()).isEqualTo(200);
        for (String action : List.of("role", "enabled", "reset-password")) {
            var failed = admin.postJson(base + "/" + action, java.util.Map.of("role", "SYSTEM_ADMIN", "enabled", false, "newPassword", "ChangedPass2026!", "version", 0));
            assertThat(failed.statusCode()).isEqualTo(409);
            assertThat(failed.body()).contains("ACCOUNT_CHANGED").doesNotContain("password_hash", "UPDATE", "stackTrace");
        }
        assertThat(admin.account("student").path("version").asLong()).isEqualTo(1);
        assertThat(new Browser().login("student", PASSWORD).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void auditStorageFailureRollsBackCreationPasswordAndDisableTogether() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        var student = new Browser(); student.login("student", PASSWORD);
        long id = admin.account("student").path("id").asLong();
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT ck_test_reject_audit CHECK (action NOT IN ('ACCOUNT_CREATED','ACCOUNT_PASSWORD_RESET','ACCOUNT_DISABLED'))");
        try {
            var create = admin.postJson("/api/identity/accounts", java.util.Map.of("username", "rollback_user", "displayName", "回滚", "role", "STUDENT", "password", PASSWORD));
            assertThat(create.statusCode()).isEqualTo(500);
            assertThat(json.readTree(admin.get("/api/identity/accounts?q=rollback_user").body()).path("total").asInt()).isZero();
            assertThat(new Browser().login("rollback_user", PASSWORD).statusCode()).isEqualTo(401);
            for (String action : List.of("reset-password", "enabled")) {
                var failed = admin.postJson("/api/identity/accounts/" + id + "/" + action, java.util.Map.of("newPassword", "RollbackPass2026!", "enabled", false, "version", 0));
                assertThat(failed.statusCode()).isEqualTo(500);
                assertThat(failed.body()).contains("SERVER_ERROR").doesNotContain("RollbackPass", "INSERT", "operation_log", "$2a$");
                assertThat(student.get("/api/auth/me").statusCode()).isEqualTo(200);
                assertThat(admin.account("student").path("version").asLong()).isZero();
                assertThat(new Browser().login("student", PASSWORD).statusCode()).isEqualTo(200);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK ck_test_reject_audit"); }
    }

    @Test
    void lastAdministratorCannotBeDisabledOrDemotedEvenConcurrently() throws Exception {
        var first = new Browser(); first.login("system_admin", PASSWORD);
        long firstId = first.account("system_admin").path("id").asLong();
        for (String action : List.of("role", "enabled")) {
            var rejected = first.postJson("/api/identity/accounts/" + firstId + "/" + action,
                    java.util.Map.of("role", "STUDENT", "enabled", false, "version", 0));
            assertThat(rejected.statusCode()).isEqualTo(409);
            assertThat(rejected.body()).contains("LAST_ADMINISTRATOR", "先创建或启用");
        }
        var created = first.postJson("/api/identity/accounts", java.util.Map.of("username", "second_admin", "displayName", "另一位系统管理员", "role", "SYSTEM_ADMIN", "password", PASSWORD));
        long secondId = json.readTree(created.body()).path("id").asLong();
        var second = new Browser(); second.login("second_admin", PASSWORD);
        var latch = new java.util.concurrent.CountDownLatch(1);
        var a = java.util.concurrent.CompletableFuture.supplyAsync(() -> concurrentDemotion(first, firstId, latch));
        var b = java.util.concurrent.CompletableFuture.supplyAsync(() -> concurrentDemotion(second, secondId, latch));
        latch.countDown();
        assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE enabled=true AND role='SYSTEM_ADMIN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='ACCOUNT_ROLE_CHANGED'", Integer.class)).isEqualTo(1);
    }

    private int concurrentDemotion(Browser browser, long id, java.util.concurrent.CountDownLatch latch) {
        try { latch.await(); return browser.postJson("/api/identity/accounts/" + id + "/role", java.util.Map.of("role", "STUDENT", "version", 0)).statusCode(); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return send(path, "GET", null, null, null); }
        HttpResponse<String> login(String username, String password) throws Exception {
            return send("/api/auth/login", "POST", "application/x-www-form-urlencoded",
                    "username=" + username + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8), csrf());
        }
        JsonNode account(String username) throws Exception {
            var response = get("/api/identity/accounts?q=" + username);
            assertThat(response.statusCode()).isEqualTo(200);
            return json.readTree(response.body()).path("items").get(0);
        }
        HttpResponse<String> postJson(String path, Object body) throws Exception {
            return send(path, "POST", "application/json", json.writeValueAsString(body), csrf());
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
