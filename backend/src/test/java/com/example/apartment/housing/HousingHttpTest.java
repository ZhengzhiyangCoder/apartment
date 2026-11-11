package com.example.apartment.housing;

import com.example.apartment.testing.HttpIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HousingHttpTest extends HttpIntegrationTest {
    @Test
    void administratorMaintainsPersistentBuildingsWithPaginationAndAudit() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        var created = admin.postJson("/api/housing/buildings", Map.of("name", "模拟 A 栋"));
        assertThat(created.statusCode()).isEqualTo(201);
        var building = json.readTree(created.body());
        long id = building.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(admin.postJson("/api/housing/buildings", Map.of("name", "模拟 A 栋")).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/buildings", Map.of("name", "模拟 B 栋")).statusCode()).isEqualTo(201);
        var page = json.readTree(admin.get("/api/housing/buildings?page=2&size=1").body());
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(admin.postJson("/api/housing/buildings/" + id, Map.of("name", "模拟 A1 栋", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/buildings/" + id + "/enabled", Map.of("enabled", false, "version", 1)).statusCode()).isEqualTo(200);
        var disabled = json.readTree(admin.get("/api/housing/buildings?enabled=false").body());
        assertThat(disabled.path("total").asInt()).isEqualTo(1);
        assertThat(disabled.path("items").get(0).path("name").asText()).isEqualTo("模拟 A1 栋");
        assertThat(admin.postJson("/api/housing/buildings/" + id, Map.of("name", "过期编辑", "version", 0)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/buildings/" + id + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT action FROM operation_log WHERE target_type = 'BUILDING' ORDER BY id")
                .stream().map(r -> r.get("action"))).containsExactly("BUILDING_CREATED", "BUILDING_CREATED", "BUILDING_UPDATED", "BUILDING_DISABLED", "BUILDING_ENABLED");
    }
    @Test
    void roomsAndBedsAreUniqueWithinTheirParentAndRespectDisabledAncestors() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        long a = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "A 栋")).body()).path("id").asLong();
        long b = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "B 栋")).body()).path("id").asLong();
        var response = admin.postJson("/api/housing/rooms", Map.of("buildingId", a, "roomNo", "101", "floor", 1));
        assertThat(response.statusCode()).isEqualTo(201);
        long room = json.readTree(response.body()).path("id").asLong();
        assertThat(admin.postJson("/api/housing/rooms", Map.of("buildingId", a, "roomNo", "101", "floor", 1)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/rooms", Map.of("buildingId", b, "roomNo", "101", "floor", 1)).statusCode()).isEqualTo(201);
        var bedResponse = admin.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "01"));
        assertThat(bedResponse.statusCode()).isEqualTo(201);
        long bed = json.readTree(bedResponse.body()).path("id").asLong();
        assertThat(admin.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "01")).statusCode()).isEqualTo(409);
        var otherRoom = json.readTree(admin.postJson("/api/housing/rooms", Map.of("buildingId", a, "roomNo", "102", "floor", 1)).body()).path("id").asLong();
        assertThat(admin.postJson("/api/housing/beds", Map.of("roomId", otherRoom, "bedNo", "01")).statusCode()).isEqualTo(201);
        assertThat(admin.postJson("/api/housing/rooms/" + room, Map.of("roomNo", "A101", "floor", 2, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/beds/" + bed, Map.of("bedNo", "02", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/housing/rooms?buildingId=" + a + "&q=A101").body()).path("total").asInt()).isEqualTo(1);
        var beds = json.readTree(admin.get("/api/housing/beds?roomId=" + room).body());
        assertThat(beds.path("items").get(0).path("bedNo").asText()).isEqualTo("02");
        assertThat(beds.path("items").get(0).path("buildingName").asText()).isEqualTo("A 栋");
        assertThat(admin.postJson("/api/housing/buildings/" + a + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/rooms", Map.of("buildingId", a, "roomNo", "103", "floor", 1)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "03")).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/beds/" + bed + "/enabled", Map.of("enabled", false, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/beds/" + bed + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/buildings/" + a + "/enabled", Map.of("enabled", true, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/rooms/" + room + "/enabled", Map.of("enabled", false, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/beds/" + bed + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/rooms/" + room + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/beds/" + bed + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(200);
    }
    @Test
    void studentProfileLinksAnExistingStudentAndKeepsIdentityConsistent() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        long account = admin.account("student").path("id").asLong();
        var input = Map.of("userId", account, "studentNo", "20260001", "fullName", "模拟学生甲", "college", "计算机学院", "major", "软件工程", "className", "2026 软件一班");
        var response = admin.postJson("/api/housing/students", input);
        assertThat(response.statusCode()).isEqualTo(201);
        var profile = json.readTree(response.body()); long id = profile.path("id").asLong();
        assertThat(response.body()).doesNotContain("password", "hash", "role");
        assertThat(profile.path("userId").asLong()).isEqualTo(account);
        assertThat(admin.postJson("/api/housing/students", input).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/identity/accounts/" + account + "/role", Map.of("role", "MAINTAINER", "version", 0)).statusCode()).isEqualTo(409);
        assertThat(admin.account("student").path("role").asText()).isEqualTo("STUDENT");
        assertThat(admin.postJson("/api/housing/students/" + id, Map.of("studentNo", "20260002", "fullName", "模拟学生乙", "college", "信息学院", "major", "网络工程", "className", "网络一班", "version", 0)).statusCode()).isEqualTo(200);
        var found = json.readTree(admin.get("/api/housing/students?q=20260002&size=1").body());
        assertThat(found.path("total").asInt()).isEqualTo(1);
        assertThat(found.path("items").get(0).path("fullName").asText()).isEqualTo("模拟学生乙");
        assertThat(admin.postJson("/api/housing/students/" + id + "/enabled", Map.of("enabled", false, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/identity/accounts/" + account + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/students/" + id + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/identity/accounts/" + account + "/enabled", Map.of("enabled", true, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/students/" + id + "/enabled", Map.of("enabled", true, "version", 2)).statusCode()).isEqualTo(200);
        for (String username : java.util.List.of("system_admin", "dorm_manager", "maintainer", "disabled")) {
            long invalid = admin.account(username).path("id").asLong();
            assertThat(admin.postJson("/api/housing/students", Map.of("userId", invalid, "studentNo", "20269999", "fullName", "错误关联", "college", "计算机学院", "major", "软件工程", "className", "一班")).statusCode()).isEqualTo(409);
        }
    }
    @Test
    void housingAuthorizationUsesTheSessionAndDormManagersOnlyReadLocations() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        var anonymous = new Browser();
        var studentId = admin.account("student").path("id").asLong();
        for (String resource : java.util.List.of("buildings", "rooms", "beds", "students")) {
            String path = "/api/housing/" + resource;
            assertThat(anonymous.get(path).statusCode()).isEqualTo(401);
            assertThat(anonymous.postJson(path, Map.of()).statusCode()).isEqualTo(401);
            assertThat(admin.send(path, "POST", "application/json", "{}", null).statusCode()).isEqualTo(403);
            assertThat(admin.send(path + "/1", "DELETE", null, null, admin.csrf()).statusCode()).isEqualTo(403);
            for (String role : java.util.List.of("dorm_manager", "student", "maintainer")) {
                var user = new Browser(); user.login(role, PASSWORD);
                assertThat(user.get(path + "?userId=" + studentId + "&role=SYSTEM_ADMIN").statusCode())
                        .isEqualTo(role.equals("dorm_manager") && !resource.equals("students") ? 200 : 403);
                for (String suffix : java.util.List.of("", "/1", "/1/enabled")) {
                    assertThat(user.postJson(path + suffix, Map.of("actorId", admin.account("system_admin").path("id").asLong(), "enabled", false, "version", 0)).statusCode()).isEqualTo(403);
                }
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isZero();
    }

    @Test
    void invalidInputsMissingParentsAndStaleEditsFailWithoutChangingCatalogs() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        for (String resource : java.util.List.of("buildings", "rooms", "beds", "students")) {
            String path = "/api/housing/" + resource;
            for (String query : java.util.List.of("page=0", "size=101", "size=0", "enabled=oops", "page=2147483648", "q=" + "x".repeat(65))) {
                assertThat(admin.get(path + "?" + query).statusCode()).isEqualTo(400);
            }
            assertThat(admin.postJson(path, Map.of()).statusCode()).isEqualTo(400);
            assertThat(admin.postJson(path + "/99999999/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(404);
            assertThat(admin.postJson(path + "/1/enabled", Map.of("enabled", false, "version", -1)).statusCode()).isEqualTo(400);
        }
        assertThat(admin.postJson("/api/housing/buildings", Map.of("name", " ")).statusCode()).isEqualTo(400);
        assertThat(admin.postJson("/api/housing/rooms", Map.of("buildingId", 99999999, "roomNo", "101", "floor", 1)).statusCode()).isEqualTo(404);
        assertThat(admin.postJson("/api/housing/beds", Map.of("roomId", 99999999, "bedNo", "01")).statusCode()).isEqualTo(404);
        assertThat(admin.get("/api/housing/rooms?buildingId=-1").statusCode()).isEqualTo(400);
        assertThat(admin.get("/api/housing/beds?roomId=0").statusCode()).isEqualTo(400);
        long building = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "参数模拟楼栋")).body()).path("id").asLong();
        for (Object floor : java.util.List.of(0, 101, 1.5)) {
            assertThat(admin.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", floor)).statusCode()).isEqualTo(400);
        }
        assertThat(json.readTree(admin.get("/api/housing/rooms").body()).path("total").asInt()).isZero();
    }

    @Test
    void auditFailureRollsBackEveryCatalogCreateUpdateAndDisable() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        long building = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "回滚模拟楼栋")).body()).path("id").asLong();
        long room = json.readTree(admin.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", 1)).body()).path("id").asLong();
        long bed = json.readTree(admin.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "01")).body()).path("id").asLong();
        long user = admin.account("student").path("id").asLong();
        var studentInput = Map.of("userId", user, "studentNo", "ROLL001", "fullName", "回滚模拟学生", "college", "模拟学院", "major", "模拟专业", "className", "模拟班级");
        long student = json.readTree(admin.postJson("/api/housing/students", studentInput).body()).path("id").asLong();
        long user2 = json.readTree(admin.postJson("/api/identity/accounts", Map.of("username", "rollback_student", "displayName", "模拟学生", "role", "STUDENT", "password", PASSWORD)).body()).path("id").asLong();
        var ids = Map.of("buildings", building, "rooms", room, "beds", bed, "students", student);
        var creates = Map.of("buildings", Map.of("name", "不应留下的楼栋"), "rooms", Map.of("buildingId", building, "roomNo", "102", "floor", 1),
                "beds", Map.of("roomId", room, "bedNo", "02"), "students", Map.of("userId", user2, "studentNo", "ROLL002", "fullName", "不应留下的资料", "college", "模拟学院", "major", "模拟专业", "className", "模拟班级"));
        var updates = Map.of("buildings", Map.of("name", "修改后名称", "version", 0), "rooms", Map.of("roomNo", "103", "floor", 3, "version", 0),
                "beds", Map.of("bedNo", "03", "version", 0), "students", Map.of("studentNo", "ROLL003", "fullName", "修改后学生", "college", "另一学院", "major", "另一专业", "className", "另一班级", "version", 0));
        // Clear setup-only audit in the isolated fixture before rejecting all new housing audit writes.
        jdbc.update("DELETE FROM operation_log");
        long logCount = 0;
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT ck_task03_fail_audit CHECK (target_type = 'ACCOUNT')");
        try {
            for (String resource : ids.keySet()) {
                String path = "/api/housing/" + resource;
                String before = admin.get(path).body();
                for (var response : java.util.List.of(admin.postJson(path, creates.get(resource)), admin.postJson(path + "/" + ids.get(resource), updates.get(resource)),
                        admin.postJson(path + "/" + ids.get(resource) + "/enabled", Map.of("enabled", false, "version", 0)))) {
                    assertThat(response.statusCode()).isEqualTo(500);
                    assertThat(response.body()).doesNotContain("SQL", "password", "INSERT", "stackTrace", "constraint");
                }
                assertThat(admin.get(path).body()).isEqualTo(before);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isEqualTo(logCount);
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK ck_task03_fail_audit"); }
    }

    @Test
    void concurrentDuplicatesEditsAndStudentRoleChangesCannotBreakInvariants() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        var other = new Browser(); other.login("system_admin", PASSWORD);
        long building = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "并发模拟楼栋")).body()).path("id").asLong();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var f1 = pool.submit(() -> { start.await(); return admin.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", 1)).statusCode(); });
            var f2 = pool.submit(() -> { start.await(); return other.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", 1)).statusCode(); });
            start.countDown(); assertThat(java.util.List.of(f1.get(), f2.get())).containsExactlyInAnyOrder(201, 409);
            long room = json.readTree(admin.get("/api/housing/rooms").body()).path("items").get(0).path("id").asLong();
            var bedStart = new java.util.concurrent.CountDownLatch(1);
            var b1 = pool.submit(() -> { bedStart.await(); return admin.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "01")).statusCode(); });
            var b2 = pool.submit(() -> { bedStart.await(); return other.postJson("/api/housing/beds", Map.of("roomId", room, "bedNo", "01")).statusCode(); });
            bedStart.countDown(); assertThat(java.util.List.of(b1.get(), b2.get())).containsExactlyInAnyOrder(201, 409);
            var editStart = new java.util.concurrent.CountDownLatch(1);
            var e1 = pool.submit(() -> { editStart.await(); return admin.postJson("/api/housing/rooms/" + room, Map.of("roomNo", "A101", "floor", 1, "version", 0)).statusCode(); });
            var e2 = pool.submit(() -> { editStart.await(); return other.postJson("/api/housing/rooms/" + room, Map.of("roomNo", "B101", "floor", 1, "version", 0)).statusCode(); });
            editStart.countDown(); assertThat(java.util.List.of(e1.get(), e2.get())).containsExactlyInAnyOrder(200, 409);
            long user = admin.account("student").path("id").asLong();
            var linkStart = new java.util.concurrent.CountDownLatch(1);
            var p1 = pool.submit(() -> { linkStart.await(); return admin.postJson("/api/housing/students", Map.of("userId", user, "studentNo", "RACE001", "fullName", "并发模拟学生", "college", "模拟学院", "major", "模拟专业", "className", "模拟班级")).statusCode(); });
            var p2 = pool.submit(() -> { linkStart.await(); return other.postJson("/api/identity/accounts/" + user + "/role", Map.of("role", "MAINTAINER", "version", 0)).statusCode(); });
            linkStart.countDown(); var statuses = java.util.List.of(p1.get(), p2.get());
            assertThat(statuses).contains(409);
            var role = admin.account("student").path("role").asText();
            var count = json.readTree(admin.get("/api/housing/students").body()).path("total").asInt();
            assertThat(role.equals("STUDENT") && count == 1 || role.equals("MAINTAINER") && count == 0).isTrue();
            assertThat(statuses).contains(role.equals("STUDENT") ? 201 : 200);
        } finally { pool.shutdownNow(); }
    }
    @Test
    void allCatalogsPageFilterKeepBindingsAndStoreActorAndSnapshots() throws Exception {
        var admin = new Browser(); admin.login("system_admin", PASSWORD);
        long actorId = admin.account("system_admin").path("id").asLong();
        long building = json.readTree(admin.postJson("/api/housing/buildings", Map.of("name", "分页模拟楼栋", "actorId", 99999)).body()).path("id").asLong();
        long room1 = json.readTree(admin.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", 1)).body()).path("id").asLong();
        long room2 = json.readTree(admin.postJson("/api/housing/rooms", Map.of("buildingId", building, "roomNo", "102", "floor", 1)).body()).path("id").asLong();
        long bed1 = json.readTree(admin.postJson("/api/housing/beds", Map.of("roomId", room1, "bedNo", "01")).body()).path("id").asLong();
        admin.postJson("/api/housing/beds", Map.of("roomId", room1, "bedNo", "02"));
        long user1 = admin.account("student").path("id").asLong();
        long user2 = json.readTree(admin.postJson("/api/identity/accounts", Map.of("username", "second_student", "displayName", "第二模拟学生", "role", "STUDENT", "password", PASSWORD)).body()).path("id").asLong();
        long profile1 = json.readTree(admin.postJson("/api/housing/students", Map.of("userId", user1, "studentNo", "PAGE01", "fullName", "第一学生", "college", "模拟学院", "major", "软件工程", "className", "一班")).body()).path("id").asLong();
        assertThat(admin.postJson("/api/housing/students", Map.of("userId", user2, "studentNo", "PAGE01", "fullName", "第二学生", "college", "模拟学院", "major", "软件工程", "className", "二班")).statusCode()).isEqualTo(409);
        assertThat(admin.postJson("/api/housing/students", Map.of("userId", user2, "studentNo", "PAGE02", "fullName", "第二学生", "college", "模拟学院", "major", "软件工程", "className", "二班")).statusCode()).isEqualTo(201);
        for (String resource : java.util.List.of("rooms", "beds", "students")) {
            var page = json.readTree(admin.get("/api/housing/" + resource + "?page=2&size=1").body());
            assertThat(page.path("total").asInt()).isEqualTo(2);
            assertThat(page.path("items").size()).isEqualTo(1);
            assertThat(json.readTree(admin.get("/api/housing/" + resource + "?q=not_present").body()).path("total").asInt()).isZero();
        }
        assertThat(admin.postJson("/api/housing/beds/" + bed1, Map.of("roomId", room2, "bedNo", "03", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/housing/beds?roomId=" + room1).body()).path("total").asInt()).isEqualTo(2);
        assertThat(json.readTree(admin.get("/api/housing/beds?roomId=" + room2).body()).path("total").asInt()).isZero();
        assertThat(admin.postJson("/api/housing/students/" + profile1, Map.of("userId", user2, "studentNo", "PAGE01", "fullName", "更正姓名", "college", "模拟学院", "major", "软件工程", "className", "一班", "version", 0)).statusCode()).isEqualTo(200);
        var after = json.readTree(admin.get("/api/housing/students?q=PAGE01").body()).path("items").get(0);
        assertThat(after.path("userId").asLong()).isEqualTo(user1);
        assertThat(after.path("fullName").asText()).isEqualTo("更正姓名");
        var logs = jdbc.queryForList("SELECT actor_id, action, before_data, after_data, created_at FROM operation_log WHERE target_type <> 'ACCOUNT' ORDER BY id");
        assertThat(logs).hasSize(9);
        assertThat(logs.toString()).doesNotContain("password", "$2a$", PASSWORD);
        for (var log : logs) {
            assertThat(log.get("actor_id")).isEqualTo(actorId);
            assertThat(log.get("created_at")).isNotNull();
        }
        assertThat(json.readTree(logs.get(8).get("before_data").toString()).path("fullName").asText()).isEqualTo("第一学生");
        assertThat(json.readTree(logs.get(8).get("after_data").toString()).path("fullName").asText()).isEqualTo("更正姓名");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE target_type <> 'ACCOUNT'", Long.class)).isEqualTo(9);
    }
}
