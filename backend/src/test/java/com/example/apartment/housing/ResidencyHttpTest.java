package com.example.apartment.housing;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResidencyHttpTest extends HttpIntegrationTest {
    private Browser admin() throws Exception {
        var browser = new Browser(); assertThat(browser.login("system_admin", PASSWORD).statusCode()).isEqualTo(200); return browser;
    }
    private JsonNode create(Browser admin, String resource, Object input) throws Exception {
        var response = admin.postJson("/api/" + resource, input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body());
    }
    private long profile(Browser admin, String username) throws Exception {
        var matches = json.readTree(admin.get("/api/identity/accounts?q=" + username).body()).path("items");
        long user = java.util.stream.StreamSupport.stream(matches.spliterator(), false)
                .filter(row -> row.path("username").asText().equals(username)).findFirst().orElseThrow().path("id").asLong();
        return create(admin, "housing/students", Map.of("userId", user, "studentNo", username, "fullName", "模拟" + username,
                "college", "模拟学院", "major", "模拟专业", "className", "模拟班级")).path("id").asLong();
    }
    private JsonNode bed(Browser admin, String name) throws Exception {
        long building = create(admin, "housing/buildings", Map.of("name", name)).path("id").asLong();
        long room = create(admin, "housing/rooms", Map.of("buildingId", building, "roomNo", "101", "floor", 1)).path("id").asLong();
        return create(admin, "housing/beds", Map.of("roomId", room, "bedNo", "01"));
    }
    private JsonNode current(Browser student) throws Exception {
        var response = student.get("/api/housing/my-residency");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).path("current");
    }

    @Test
    void administratorChecksInAndStudentOnlyReadsTheirOwnCurrentResidence() throws Exception {
        var admin = admin(); var student = new Browser(); student.login("student", PASSWORD);
        assertThat(current(student).isNull()).isTrue();
        long profile = profile(admin, "student"); var bed = bed(admin, "模拟 A 栋");
        var residency = create(admin, "housing/residencies", Map.of("studentId", profile, "bedId", bed.path("id").asLong()));
        assertThat(current(student).path("id").asLong()).isEqualTo(residency.path("id").asLong());
        assertThat(current(student).path("buildingName").asText()).isEqualTo("模拟 A 栋");
        assertThat(current(student).path("roomNo").asText()).isEqualTo("101");
        assertThat(current(student).path("bedNo").asText()).isEqualTo("01");
        assertThat(admin.postJson("/api/housing/residencies", Map.of("studentId", profile, "bedId", bed.path("id").asLong())).statusCode()).isEqualTo(409);
        var rows = json.readTree(admin.get("/api/housing/residencies?studentId=" + profile).body());
        assertThat(rows.path("total").asInt()).isEqualTo(1);
        assertThat(rows.path("items").get(0).path("startedAt").asText()).isNotBlank();
        assertThat(rows.path("items").get(0).path("endedAt").isNull()).isTrue();
        assertThat(student.get("/api/housing/residencies").statusCode()).isEqualTo(403);
        assertThat(json.readTree(student.get("/api/housing/my-residency?studentId=99999&userId=1").body()).path("current").path("id")).isEqualTo(residency.path("id"));
    }
    @Test
    void transferAndCheckoutKeepHistoryAndRejectStaleCommands() throws Exception {
        var admin = admin(); var student = new Browser(); student.login("student", PASSWORD);
        long profile = profile(admin, "student"); var oldBed = bed(admin, "模拟原楼栋"); var newBed = bed(admin, "模拟新楼栋");
        var original = create(admin, "housing/residencies", Map.of("studentId", profile, "bedId", oldBed.path("id").asLong()));
        String path = "/api/housing/residencies/" + original.path("id").asLong();
        var transfer = admin.postJson(path + "/transfer", Map.of("bedId", newBed.path("id").asLong(), "note", "模拟调寝"));
        assertThat(transfer.statusCode()).as(transfer.body()).isEqualTo(200);
        var moved = json.readTree(transfer.body());
        assertThat(current(student).path("bedId")).isEqualTo(newBed.path("id"));
        assertThat(admin.postJson(path + "/checkout", Map.of("note", "旧窗口退宿")).statusCode()).isEqualTo(409);
        assertThat(admin.postJson(path + "/transfer", Map.of("bedId", oldBed.path("id").asLong(), "note", "重复请求")).statusCode()).isEqualTo(409);
        var history = json.readTree(admin.get("/api/housing/residencies?current=false&studentId=" + profile).body());
        assertThat(history.path("total").asInt()).isEqualTo(1);
        assertThat(history.path("items").get(0).path("endReason").asText()).isEqualTo("TRANSFER");
        assertThat(history.path("items").get(0).path("endNote").asText()).isEqualTo("模拟调寝");
        assertThat(admin.postJson("/api/housing/buildings/" + oldBed.path("buildingId").asLong(), Map.of("name", "原楼改名", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/housing/residencies?current=false").body()).path("items").get(0).path("buildingName").asText()).isEqualTo("模拟原楼栋");
        var checkout = admin.postJson("/api/housing/residencies/" + moved.path("id").asLong() + "/checkout", Map.of("note", "模拟毕业退宿"));
        assertThat(checkout.statusCode()).isEqualTo(200);
        assertThat(current(student).isNull()).isTrue();
        assertThat(json.readTree(admin.get("/api/housing/residencies?current=false&studentId=" + profile).body()).path("total").asInt()).isEqualTo(2);
        assertThat(json.readTree(admin.get("/api/housing/vacant-beds").body()).path("total").asInt()).isEqualTo(2);
        create(admin, "housing/residencies", Map.of("studentId", profile, "bedId", oldBed.path("id").asLong()));
        assertThat(json.readTree(admin.get("/api/housing/residencies?studentId=" + profile + "&page=2&size=1").body()).path("items").get(0).path("id")).isEqualTo(moved.path("id"));
    }

    @Test
    void failureDuringTransferOrAuditRestoresOriginalResidenceAndLogs() throws Exception {
        var admin = admin(); var student = new Browser(); student.login("student", PASSWORD);
        long profile = profile(admin, "student"); var oldBed = bed(admin, "回滚原楼栋"); var newBed = bed(admin, "回滚新楼栋");
        var original = create(admin, "housing/residencies", Map.of("studentId", profile, "bedId", oldBed.path("id").asLong()));
        String path = "/api/housing/residencies/" + original.path("id").asLong();
        var before = json.readTree(admin.get("/api/housing/residencies").body());
        long logs = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class);
        jdbc.execute("ALTER TABLE housing_residency ADD CONSTRAINT test_reject_destination CHECK (bed_id <> " + newBed.path("id").asLong() + ")");
        try {
            var failed = admin.postJson(path + "/transfer", Map.of("bedId", newBed.path("id").asLong(), "note", "插入阶段故障"));
            assertThat(failed.statusCode()).isEqualTo(500);
            assertThat(failed.body()).doesNotContain("SQL", "Exception", "test_reject");
            assertThat(json.readTree(admin.get("/api/housing/residencies").body())).isEqualTo(before);
            assertThat(current(student).path("id")).isEqualTo(original.path("id"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isEqualTo(logs);
        } finally { jdbc.execute("ALTER TABLE housing_residency DROP CHECK test_reject_destination"); }
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_reject_residency_audit CHECK (action NOT IN ('RESIDENCY_TRANSFERRED', 'RESIDENCY_CHECKED_OUT'))");
        try {
            assertThat(admin.postJson(path + "/transfer", Map.of("bedId", newBed.path("id").asLong(), "note", "审计故障")).statusCode()).isEqualTo(500);
            assertThat(admin.postJson(path + "/checkout", Map.of("note", "退宿审计故障")).statusCode()).isEqualTo(500);
            assertThat(json.readTree(admin.get("/api/housing/residencies").body())).isEqualTo(before);
            assertThat(current(student).path("id")).isEqualTo(original.path("id"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isEqualTo(logs);
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK test_reject_residency_audit"); }
    }

    @Test
    void occupiedReferencesCannotBeDisabledAndHistorySurvivesAfterCheckout() throws Exception {
        var admin = admin(); long profile = profile(admin, "student"); var bed = bed(admin, "停用保护楼栋");
        var original = create(admin, "housing/residencies", Map.of("studentId", profile, "bedId", bed.path("id").asLong()));
        var paths = java.util.List.of("/api/identity/accounts/" + admin.account("student").path("id").asLong(),
                "/api/housing/students/" + profile, "/api/housing/buildings/" + bed.path("buildingId").asLong(),
                "/api/housing/rooms/" + bed.path("roomId").asLong(), "/api/housing/beds/" + bed.path("id").asLong());
        for (String path : paths) {
            var response = admin.postJson(path + "/enabled", Map.of("enabled", false, "version", 0));
            assertThat(response.statusCode()).as(path + ": " + response.body()).isEqualTo(409);
            assertThat(response.body()).contains("当前入住");
        }
        assertThat(admin.postJson("/api/housing/residencies/" + original.path("id").asLong() + "/checkout", Map.of("note", "先办理退宿")).statusCode()).isEqualTo(200);
        for (String path : paths) {
            assertThat(admin.postJson(path + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
            assertThat(admin.send(path, "DELETE", null, null, admin.csrf()).statusCode()).isEqualTo(403);
        }
        var history = json.readTree(admin.get("/api/housing/residencies?current=false").body());
        assertThat(history.path("total").asInt()).isEqualTo(1);
        assertThat(history.path("items").get(0).path("buildingName").asText()).isEqualTo("停用保护楼栋");
    }

    @Test
    void concurrentStudentsCompeteForOneBedAndOneStudentCannotTakeTwoBeds() throws Exception {
        var admin = admin(); var other = admin();
        create(admin, "identity/accounts", Map.of("username", "student_two", "displayName", "模拟学生乙", "role", "STUDENT", "password", PASSWORD));
        long first = profile(admin, "student"), second = profile(admin, "student_two");
        var bed = bed(admin, "竞争床位楼栋"); var spare = bed(admin, "另一床位楼栋");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return admin.postJson("/api/housing/residencies", Map.of("studentId", first, "bedId", bed.path("id").asLong())).statusCode(); });
            var b = pool.submit(() -> { start.await(); return other.postJson("/api/housing/residencies", Map.of("studentId", second, "bedId", bed.path("id").asLong())).statusCode(); });
            start.countDown(); assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
            var current = json.readTree(admin.get("/api/housing/residencies?current=true").body());
            assertThat(current.path("total").asInt()).isEqualTo(1);
            var winner = current.path("items").get(0);
            assertThat(json.readTree(admin.get("/api/housing/vacant-beds").body()).path("total").asInt()).isEqualTo(1);
            assertThat(admin.postJson("/api/housing/residencies/" + winner.path("id").asLong() + "/checkout", Map.of("note", "重新开始单人竞争")).statusCode()).isEqualTo(200);
            var again = new java.util.concurrent.CountDownLatch(1);
            var c = pool.submit(() -> { again.await(); return admin.postJson("/api/housing/residencies", Map.of("studentId", first, "bedId", bed.path("id").asLong())).statusCode(); });
            var d = pool.submit(() -> { again.await(); return other.postJson("/api/housing/residencies", Map.of("studentId", first, "bedId", spare.path("id").asLong())).statusCode(); });
            again.countDown(); assertThat(java.util.List.of(c.get(), d.get())).containsExactlyInAnyOrder(201, 409);
            assertThat(json.readTree(admin.get("/api/housing/residencies?current=true").body()).path("total").asInt()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentDeactivationAndCheckInCannotLeaveAnOccupiedDisabledParent() throws Exception {
        var admin = admin(); var other = admin(); long student = profile(admin, "student");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (String kind : java.util.List.of("account", "student", "building", "room", "bed")) {
                var bed = bed(admin, "并发停用 " + kind);
                String path = switch (kind) {
                    case "account" -> "/api/identity/accounts/" + admin.account("student").path("id").asLong();
                    case "student" -> "/api/housing/students/" + student;
                    case "building" -> "/api/housing/buildings/" + bed.path("buildingId").asLong();
                    case "room" -> "/api/housing/rooms/" + bed.path("roomId").asLong();
                    default -> "/api/housing/beds/" + bed.path("id").asLong();
                };
                var start = new java.util.concurrent.CountDownLatch(1);
                var a = pool.submit(() -> { start.await(); return admin.postJson("/api/housing/residencies", Map.of("studentId", student, "bedId", bed.path("id").asLong())).statusCode(); });
                var b = pool.submit(() -> { start.await(); return other.postJson(path + "/enabled", Map.of("enabled", false, "version", 0)).statusCode(); });
                start.countDown(); int checkedIn = a.get(), disabled = b.get();
                assertThat(checkedIn == 201 && disabled == 409 || checkedIn == 409 && disabled == 200).isTrue();
                var rows = json.readTree(admin.get("/api/housing/residencies?current=true").body());
                if (checkedIn == 201) {
                    assertThat(rows.path("total").asInt()).isEqualTo(1);
                    assertThat(admin.postJson("/api/housing/residencies/" + rows.path("items").get(0).path("id").asLong() + "/checkout", Map.of("note", "释放测试床位")).statusCode()).isEqualTo(200);
                } else {
                    assertThat(rows.path("total").asInt()).isZero();
                    assertThat(admin.postJson(path + "/enabled", Map.of("enabled", true, "version", 1)).statusCode()).isEqualTo(200);
                }
            }
        } finally { pool.shutdownNow(); }
    }

    @Test
    void invalidAssignmentsPermissionsAndStaleTransitionsDoNotChangeResidence() throws Exception {
        var admin = admin(); long student = profile(admin, "student"); var bed = bed(admin, "校验楼栋");
        var empty = new Browser();
        assertThat(empty.get("/api/housing/my-residency").statusCode()).isEqualTo(401);
        for (String role : java.util.List.of("dorm_manager", "student", "maintainer")) {
            var user = new Browser(); user.login(role, PASSWORD);
            assertThat(user.get("/api/housing/residencies?studentId=" + student).statusCode()).isEqualTo(403);
            assertThat(user.get("/api/housing/vacant-beds").statusCode()).isEqualTo(403);
            assertThat(user.get("/api/housing/my-residency").statusCode()).isEqualTo(role.equals("student") ? 200 : 403);
            for (String suffix : java.util.List.of("", "/1/transfer", "/1/checkout")) {
                assertThat(user.postJson("/api/housing/residencies" + suffix, Map.of("studentId", student, "bedId", bed.path("id").asLong(), "note", "越权", "role", "SYSTEM_ADMIN")).statusCode()).isEqualTo(403);
            }
        }
        assertThat(admin.get("/api/housing/my-residency").statusCode()).isEqualTo(403);
        assertThat(admin.send("/api/housing/residencies", "POST", "application/json", "{}", null).statusCode()).isEqualTo(403);
        assertThat(admin.postJson("/api/housing/residencies", Map.of()).statusCode()).isEqualTo(400);
        assertThat(admin.postJson("/api/housing/residencies", Map.of("studentId", -1, "bedId", 1)).statusCode()).isEqualTo(400);
        assertThat(admin.postJson("/api/housing/residencies", Map.of("studentId", student, "bedId", 99999999)).statusCode()).isEqualTo(404);
        for (String query : java.util.List.of("studentId=0", "page=0", "size=101", "current=invalid", "q=" + "x".repeat(65))) {
            assertThat(admin.get("/api/housing/residencies?" + query).statusCode()).isEqualTo(400);
        }
        // Every level must be enabled at the time of assignment.
        var paths = java.util.List.of("/api/housing/buildings/" + bed.path("buildingId").asLong(),
                "/api/housing/rooms/" + bed.path("roomId").asLong(), "/api/housing/beds/" + bed.path("id").asLong(),
                "/api/housing/students/" + student, "/api/identity/accounts/" + admin.account("student").path("id").asLong());
        for (String path : paths) {
            assertThat(admin.postJson(path + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
            assertThat(admin.postJson("/api/housing/residencies", Map.of("studentId", student, "bedId", bed.path("id").asLong())).statusCode()).isEqualTo(409);
            assertThat(admin.postJson(path + "/enabled", Map.of("enabled", true, "version", 1)).statusCode()).isEqualTo(200);
        }
        var original = create(admin, "housing/residencies", Map.of("studentId", student, "bedId", bed.path("id").asLong()));
        String path = "/api/housing/residencies/" + original.path("id").asLong();
        for (String note : java.util.List.of("", " ", "x".repeat(201))) {
            assertThat(admin.postJson(path + "/checkout", Map.of("note", note)).statusCode()).isEqualTo(400);
        }
        assertThat(admin.postJson(path + "/transfer", Map.of("note", "同床调寝", "bedId", bed.path("id").asLong())).statusCode()).isEqualTo(409);
        var destination = bed(admin, "无效目标楼栋");
        assertThat(admin.postJson("/api/housing/beds/" + destination.path("id").asLong() + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson(path + "/transfer", Map.of("note", "停用目标", "bedId", destination.path("id").asLong())).statusCode()).isEqualTo(409);
        assertThat(json.readTree(admin.get("/api/housing/residencies").body()).path("total").asInt()).isEqualTo(1);
        assertThat(admin.send(path, "DELETE", null, null, admin.csrf()).statusCode()).isEqualTo(403);
    }

    @Test
    void databaseConstraintsPreventDoubleOccupancyAndAuditKeepsActorAndSnapshots() throws Exception {
        var admin = admin(); long student = profile(admin, "student"); var first = bed(admin, "约束原楼"); var second = bed(admin, "约束新楼");
        long actor = admin.account("system_admin").path("id").asLong();
        var original = create(admin, "housing/residencies", Map.of("studentId", student, "bedId", first.path("id").asLong(), "actorId", 999999));
        create(admin, "identity/accounts", Map.of("username", "constraint_student", "displayName", "约束学生", "role", "STUDENT", "password", PASSWORD));
        long otherStudent = profile(admin, "constraint_student");
        String sql = "INSERT INTO housing_residency(student_id,bed_id,building_name,room_no,bed_no,started_by) VALUES (?,?, '约束', '101','01',?)";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, student, second.path("id").asLong(), actor)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, otherStudent, first.path("id").asLong(), actor)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        var moved = admin.postJson("/api/housing/residencies/" + original.path("id").asLong() + "/transfer", Map.of("bedId", second.path("id").asLong(), "note", "记录前后状态"));
        assertThat(moved.statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/residencies/" + json.readTree(moved.body()).path("id").asLong() + "/checkout", Map.of("note", "结束住宿")).statusCode()).isEqualTo(200);
        var logs = jdbc.queryForList("SELECT actor_id, action, before_data, after_data, created_at FROM operation_log WHERE target_type = 'RESIDENCY' ORDER BY id");
        assertThat(logs).hasSize(3);
        assertThat(logs.stream().map(r -> r.get("action"))).containsExactly("RESIDENCY_CHECKED_IN", "RESIDENCY_TRANSFERRED", "RESIDENCY_CHECKED_OUT");
        for (var log : logs) { assertThat(log.get("actor_id")).isEqualTo(actor); assertThat(log.get("created_at")).isNotNull(); }
        assertThat(json.readTree(logs.get(1).get("before_data").toString()).path("bedId")).isEqualTo(first.path("id"));
        var after = json.readTree(logs.get(1).get("after_data").toString());
        assertThat(after.path("current").path("bedId")).isEqualTo(second.path("id"));
        assertThat(after.path("previous").path("endReason").asText()).isEqualTo("TRANSFER");
        assertThat(logs.toString()).doesNotContain("password", "hash", PASSWORD);
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_reject_checkin CHECK (action <> 'RESIDENCY_CHECKED_IN' OR target_id = " + original.path("id").asLong() + ")");
        try {
            assertThat(admin.postJson("/api/housing/residencies", Map.of("studentId", student, "bedId", first.path("id").asLong())).statusCode()).isEqualTo(500);
            assertThat(json.readTree(admin.get("/api/housing/residencies?current=true").body()).path("total").asInt()).isZero();
            assertThat(json.readTree(admin.get("/api/housing/residencies").body()).path("total").asInt()).isEqualTo(2);
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK test_reject_checkin"); }
    }

}
