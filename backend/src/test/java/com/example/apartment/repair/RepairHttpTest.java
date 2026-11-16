package com.example.apartment.repair;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RepairHttpTest extends HttpIntegrationTest {
    @Test
    void workflowRejectsInvalidAccountsRolesInputsAndCsrfWithoutChangingOrder() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student"); var worker = login("maintainer");
        moveIn(admin); long workerId = admin.account("maintainer").path("id").asLong();
        var order = json.readTree(upload(student, form()).body()); String path = "/api/repairs/" + order.path("id").asLong();
        for (long invalid : List.of(999999L, admin.account("disabled").path("id").asLong(), studentAccount(admin).path("id").asLong())) {
            assertThat(manager.postJson(path + "/assign", Map.of("assigneeId", invalid, "version", 0)).statusCode()).isEqualTo(409);
        }
        for (Object invalid : List.of(Map.of(), Map.of("assigneeId", workerId), Map.of("assigneeId", 0, "version", 0), Map.of("assigneeId", workerId, "version", -1), Map.of("assigneeId", workerId, "version", 0.5))) {
            assertThat(manager.postJson(path + "/assign", invalid).statusCode()).isEqualTo(400);
        }
        assertThat(manager.send(path + "/assign", "POST", "application/json", "{\"assigneeId\":" + workerId + ",\"version\":0}", null).statusCode()).isEqualTo(403);
        assertThat(new Browser().get("/api/repairs/maintainers").statusCode()).isEqualTo(401);
        assertThat(worker.get("/api/repairs/maintainers").statusCode()).isEqualTo(403);
        for (String query : List.of("page=0", "size=101", "q=" + "x".repeat(65))) assertThat(manager.get("/api/repairs/maintainers?" + query).statusCode()).isEqualTo(400);
        assertThat(json.readTree(manager.get("/api/repairs/maintainers?q=absent").body()).path("total").asInt()).isZero();
        assertThat(json.readTree(manager.get(path).body())).isEqualTo(order);
        assertThat(manager.postJson(path + "/assign", Map.of("assigneeId", workerId, "version", 0)).statusCode()).isEqualTo(200);
        for (var actor : List.of(student, manager, admin)) {
            assertThat(actor.postJson(path + "/start", Map.of("version", 1)).statusCode()).isEqualTo(403);
            assertThat(actor.postJson(path + "/complete", Map.of("version", 1, "completionNote", "越权完成")).statusCode()).isEqualTo(403);
        }
        assertThat(manager.postJson(path + "/reassign", Map.of("assigneeId", workerId, "version", 1, "reason", "相同负责人")).statusCode()).isEqualTo(409);
        assertThat(manager.postJson(path + "/reassign", Map.of("assigneeId", workerId, "version", 1, "reason", " ")).statusCode()).isEqualTo(400);
        assertThat(manager.postJson(path + "/reassign", Map.of("assigneeId", workerId, "version", 1, "reason", "字".repeat(501))).statusCode()).isEqualTo(400);
        assertThat(worker.postJson(path + "/start", Map.of()).statusCode()).isEqualTo(400);
        assertThat(worker.postJson(path + "/complete", Map.of("version", 1, "completionNote", "字".repeat(501))).statusCode()).isEqualTo(400);
        assertThat(worker.postJson(path + "/start", Map.of("version", 1)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/identity/accounts/" + workerId + "/enabled", Map.of("version", 0, "enabled", false)).statusCode()).isEqualTo(409);
    }

    @Test
    void competingStartCancelAndReassignmentHaveOneConsistentWinner() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var worker = login("maintainer"); moveIn(admin);
        long workerId = admin.account("maintainer").path("id").asLong();
        long otherId = create(admin, "identity/accounts", Map.of("username", "race_worker", "displayName", "竞争维修人员", "role", "MAINTAINER", "password", PASSWORD)).path("id").asLong();
        student.csrf(); worker.csrf(); admin.csrf();
        for (String competitor : List.of("cancel", "reassign", "start")) {
            var order = json.readTree(upload(student, form(), png()).body()); String path = "/api/repairs/" + order.path("id").asLong();
            assertThat(admin.postJson(path + "/assign", Map.of("assigneeId", workerId, "version", 0)).statusCode()).isEqualTo(200);
            var gate = new java.util.concurrent.CountDownLatch(1); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var start = pool.submit(() -> { gate.await(); return worker.postJson(path + "/start", Map.of("version", 1)); });
                var competing = pool.submit(() -> { gate.await(); return switch (competitor) {
                    case "cancel" -> student.postJson(path + "/cancel", Map.of("reason", "并发取消"));
                    case "reassign" -> admin.postJson(path + "/reassign", Map.of("assigneeId", otherId, "version", 1, "reason", "并发重分配"));
                    default -> worker.postJson(path + "/start", Map.of("version", 1));
                }; });
                gate.countDown(); var a = start.get(); var b = competing.get();
                assertThat(List.of(a.statusCode(), b.statusCode())).contains(200);
                assertThat(a.statusCode() == 200 ? b.statusCode() : a.statusCode()).isIn(404, 409);
                var after = json.readTree(admin.get(path).body());
                assertThat(after.path("logs").size()).isEqualTo(3); assertThat(after.path("version").asLong()).isEqualTo(2);
                assertThat(after.path("logs").get(2).path("toStatus")).isEqualTo(after.path("status"));
                if (a.statusCode() == 200 || competitor.equals("start")) {
                    assertThat(after.path("status").asText()).isEqualTo("IN_PROGRESS");
                    assertThat(after.path("assigneeId").asLong()).isEqualTo(workerId);
                } else if (competitor.equals("cancel")) assertThat(after.path("status").asText()).isEqualTo("CANCELLED");
                else {
                    assertThat(after.path("status").asText()).isEqualTo("PENDING");
                    assertThat(after.path("assigneeId").asLong()).isEqualTo(otherId);
                    assertThat(image(worker, order.path("images").get(0).path("url").asText()).statusCode()).isEqualTo(404);
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE target_type='REPAIR' AND target_id=?", Long.class, order.path("id").asLong())).isEqualTo(3);
            } finally { pool.shutdownNow(); }
        }
    }

    @Test
    void assignmentRacingAccountDisableOrRoleChangeCannotCreateAnUnusableAssignee() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student"); moveIn(admin);
        admin.csrf(); manager.csrf();
        for (String mutation : List.of("enabled", "role")) {
            var account = create(admin, "identity/accounts", Map.of("username", "race_" + mutation, "displayName", "模拟竞争账号", "role", "MAINTAINER", "password", PASSWORD));
            long workerId = account.path("id").asLong();
            var order = json.readTree(upload(student, form()).body()); String path = "/api/repairs/" + order.path("id").asLong();
            var gate = new java.util.concurrent.CountDownLatch(1); var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var assign = pool.submit(() -> { gate.await(); return manager.postJson(path + "/assign", Map.of("assigneeId", workerId, "version", 0)); });
                var change = pool.submit(() -> { gate.await(); return admin.postJson("/api/identity/accounts/" + workerId + "/" + mutation,
                        mutation.equals("enabled") ? Map.of("enabled", false, "version", 0) : Map.of("role", "DORM_MANAGER", "version", 0)); });
                gate.countDown(); var a = assign.get(); var b = change.get();
                assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 409);
                var after = json.readTree(manager.get(path).body()); var identity = admin.account("race_" + mutation);
                if (a.statusCode() == 200) {
                    assertThat(after.path("assigneeId").asLong()).isEqualTo(workerId);
                    assertThat(identity.path("enabled").asBoolean()).isTrue(); assertThat(identity.path("role").asText()).isEqualTo("MAINTAINER");
                } else assertThat(after).isEqualTo(order);
            } finally { pool.shutdownNow(); }
        }
    }

    @Test
    void failedWorkflowLogsAndAuditsRollbackStateResponsibilityTimesAndResultThenRetry() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var worker = login("maintainer"); moveIn(admin);
        long workerId = admin.account("maintainer").path("id").asLong();
        long nextId = create(admin, "identity/accounts", Map.of("username", "rollback_worker", "displayName", "模拟回滚维修员", "role", "MAINTAINER", "password", PASSWORD)).path("id").asLong();
        var next = login("rollback_worker");
        var order = json.readTree(upload(student, form(), png()).body()); String path = "/api/repairs/" + order.path("id").asLong();
        int version = 0;
        for (String action : List.of("assign", "reassign", "start", "complete")) {
            Browser actor = action.equals("assign") || action.equals("reassign") ? admin : next;
            Object body = switch (action) {
                case "assign" -> Map.of("assigneeId", workerId, "version", version);
                case "reassign" -> Map.of("assigneeId", nextId, "version", version, "reason", "模拟回滚重分配");
                case "start" -> Map.of("version", version);
                default -> Map.of("version", version, "completionNote", "模拟回滚后完成");
            };
            var before = json.readTree(admin.get(path).body());
            for (String table : List.of("repair_status_log", "operation_log")) {
                long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
                String note = switch (action) { case "assign" -> "分配维修人员"; case "reassign" -> "模拟回滚重分配"; case "start" -> "开始维修"; default -> "模拟回滚后完成"; };
                String auditAction = switch (action) { case "assign" -> "REPAIR_ASSIGNED"; case "reassign" -> "REPAIR_REASSIGNED"; case "start" -> "REPAIR_STARTED"; default -> "REPAIR_COMPLETED"; };
                String predicate = table.equals("repair_status_log") ? "note <> '" + note + "'" : "action <> '" + auditAction + "'";
                jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT test_reject_workflow CHECK (" + predicate + ")");
                try {
                    var failed = actor.postJson(path + "/" + action, body);
                    assertThat(failed.statusCode()).as(action + failed.body()).isEqualTo(500);
                    assertThat(failed.body()).doesNotContain("SQL", "Exception", "test_reject");
                    assertThat(json.readTree(admin.get(path).body())).isEqualTo(before);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isEqualTo(count);
                } finally { jdbc.execute("ALTER TABLE " + table + " DROP CHECK test_reject_workflow"); }
            }
            assertThat(actor.postJson(path + "/" + action, body).statusCode()).isEqualTo(200); version++;
        }
        assertThat(worker.get(path).statusCode()).isEqualTo(404);
        assertThat(json.readTree(student.get(path).body()).path("completionNote").asText()).isEqualTo("模拟回滚后完成");
        assertThat(admin.postJson("/api/identity/accounts/" + nextId + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(image(next, order.path("images").get(0).path("url").asText()).statusCode()).isEqualTo(401);
        assertThat(image(student, order.path("images").get(0).path("url").asText()).statusCode()).isEqualTo(200);
    }
    @Test
    void pendingOrdersCanBeCancelledAndUnfinishedAssignmentsBlockDisableAndRoleChange() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student");
        var worker = login("maintainer"); moveIn(admin);
        long workerId = admin.account("maintainer").path("id").asLong(); String accountPath = "/api/identity/accounts/" + workerId;
        for (var cancelActor : List.of(student, manager, admin)) {
            var order = json.readTree(upload(student, form(), png()).body()); String path = "/api/repairs/" + order.path("id").asLong();
            assertThat(admin.postJson(path + "/assign", Map.of("assigneeId", workerId, "version", 0)).statusCode()).isEqualTo(200);
            var disabled = admin.postJson(accountPath + "/enabled", Map.of("enabled", false, "version", 0));
            assertThat(disabled.statusCode()).as(disabled.body()).isEqualTo(409);
            assertThat(disabled.body()).contains("UNFINISHED_REPAIRS");
            assertThat(admin.postJson(accountPath + "/role", Map.of("role", "DORM_MANAGER", "version", 0)).statusCode()).isEqualTo(409);
            assertThat(worker.postJson(path + "/cancel", Map.of("reason", "维修人员不能取消")).statusCode()).isEqualTo(403);
            assertThat(cancelActor.postJson(path + "/cancel", Map.of("reason", " ")).statusCode()).isEqualTo(400);
            var cancelled = cancelActor.postJson(path + "/cancel", Map.of("reason", "模拟待处理取消"));
            assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
            var result = json.readTree(cancelled.body());
            assertThat(result.path("status").asText()).isEqualTo("CANCELLED");
            assertThat(result.path("version").asLong()).isEqualTo(2);
            assertThat(result.path("logs").get(2).path("assigneeId").asLong()).isEqualTo(workerId);
            assertThat(worker.postJson(path + "/start", Map.of("version", 2)).statusCode()).isEqualTo(409);
            assertThat(admin.postJson(path + "/reassign", Map.of("assigneeId", workerId, "version", 2, "reason", "终态重开")).statusCode()).isEqualTo(409);
            assertThat(student.get(path).statusCode()).isEqualTo(200);
        }
        assertThat(admin.postJson(accountPath + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(worker.get("/api/repairs").statusCode()).isEqualTo(401);
        assertThat(json.readTree(manager.get("/api/repairs/maintainers").body()).path("total").asInt()).isZero();
        assertThat(admin.postJson(accountPath + "/role", Map.of("role", "DORM_MANAGER", "version", 1)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/repairs").body()).path("total").asInt()).isEqualTo(3);
    }
    @Test
    void reassignmentRevokesOldAccessAndOnlyAssigneeCanStartThenCompleteWithExplanation() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student");
        var first = login("maintainer"); moveIn(admin);
        long firstId = admin.account("maintainer").path("id").asLong();
        long secondId = create(admin, "identity/accounts", Map.of("username", "second_worker", "displayName", "模拟新维修员", "role", "MAINTAINER", "password", PASSWORD)).path("id").asLong();
        var second = login("second_worker");
        var order = json.readTree(upload(student, form(), png()).body()); String path = "/api/repairs/" + order.path("id").asLong();
        String url = order.path("images").get(0).path("url").asText();
        assertThat(manager.postJson(path + "/assign", Map.of("assigneeId", firstId, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(first.postJson(path + "/complete", Map.of("version", 1, "completionNote", "跳过处理")).statusCode()).isEqualTo(409);
        var reassigned = manager.postJson(path + "/reassign", Map.of("assigneeId", secondId, "version", 1, "reason", "原负责人休假，交由新负责人处理"));
        assertThat(reassigned.statusCode()).as(reassigned.body()).isEqualTo(200);
        var changed = json.readTree(reassigned.body());
        assertThat(changed.path("status").asText()).isEqualTo("PENDING");
        assertThat(changed.path("logs").get(2).path("previousAssigneeId").asLong()).isEqualTo(firstId);
        assertThat(changed.path("logs").get(2).path("assigneeId").asLong()).isEqualTo(secondId);
        assertThat(first.get(path).statusCode()).isEqualTo(404);
        assertThat(image(first, url).statusCode()).isEqualTo(404);
        assertThat(json.readTree(first.get("/api/repairs").body()).path("total").asInt()).isZero();
        assertThat(first.postJson(path + "/start", Map.of("version", 1)).statusCode()).isEqualTo(404);
        assertThat(second.get(path).statusCode()).isEqualTo(200);
        assertThat(image(second, url).statusCode()).isEqualTo(200);
        assertThat(second.postJson(path + "/start", Map.of("version", 1)).statusCode()).isEqualTo(409);
        var started = second.postJson(path + "/start", Map.of("version", 2));
        assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
        assertThat(json.readTree(started.body()).path("startedAt").isNull()).isFalse();
        assertThat(manager.postJson(path + "/reassign", Map.of("assigneeId", firstId, "version", 3, "reason", "处理中不可转派")).statusCode()).isEqualTo(409);
        for (var actor : List.of(student, manager, admin)) assertThat(actor.postJson(path + "/cancel", Map.of("reason", "处理中不能取消")).statusCode()).isEqualTo(409);
        assertThat(second.postJson(path + "/complete", Map.of("version", 3, "completionNote", " ")).statusCode()).isEqualTo(400);
        var completed = second.postJson(path + "/complete", Map.of("version", 3, "completionNote", "更换水龙头密封圈，通水检查无渗漏"));
        assertThat(completed.statusCode()).as(completed.body()).isEqualTo(200);
        var result = json.readTree(student.get(path).body());
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("completionNote").asText()).isEqualTo("更换水龙头密封圈，通水检查无渗漏");
        assertThat(result.path("logs").size()).isEqualTo(5);
        assertThat(result.path("logs").get(4).path("actorName").asText()).isEqualTo("模拟新维修员");
        assertThat(result.path("completedAt").isNull()).isFalse();
        assertThat(result.path("version").asLong()).isEqualTo(4);
        assertThat(second.postJson(path + "/complete", Map.of("version", 4, "completionNote", "试图改写")).statusCode()).isEqualTo(409);
        assertThat(second.postJson(path + "/start", Map.of("version", 4)).statusCode()).isEqualTo(409);
        assertThat(manager.postJson(path + "/assign", Map.of("assigneeId", firstId, "version", 4)).statusCode()).isEqualTo(409);
        assertThat(json.readTree(student.get(path).body())).isEqualTo(result);
        var audit = jdbc.queryForList("SELECT action, actor_id, after_data FROM operation_log WHERE target_type='REPAIR' AND target_id=? ORDER BY id", order.path("id").asLong());
        assertThat(audit).hasSize(5);
        assertThat(audit.get(4).get("action")).isEqualTo("REPAIR_COMPLETED");
        assertThat(((Number) audit.get(4).get("actor_id")).longValue()).isEqualTo(secondId);
        assertThat(audit.get(4).get("after_data").toString()).contains("更换水龙头密封圈");
    }
    @Test
    void managerAssignsEnabledMaintainerAndOnlyCurrentAssigneeCanReadOrderAndImages() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student");
        var maintainer = login("maintainer"); moveIn(admin);
        var order = json.readTree(upload(student, form(), png()).body());
        String path = "/api/repairs/" + order.path("id").asLong();
        long worker = admin.account("maintainer").path("id").asLong();
        var options = manager.get("/api/repairs/maintainers");
        assertThat(options.statusCode()).as(options.body()).isEqualTo(200);
        assertThat(json.readTree(options.body()).path("total").asInt()).isEqualTo(1);
        assertThat(options.body()).doesNotContain("password", "authVersion");
        var assigned = manager.postJson(path + "/assign", Map.of("assigneeId", worker, "version", 0));
        assertThat(assigned.statusCode()).as(assigned.body()).isEqualTo(200);
        var after = json.readTree(assigned.body());
        assertThat(after.path("status").asText()).isEqualTo("PENDING");
        assertThat(after.path("assigneeId").asLong()).isEqualTo(worker);
        assertThat(after.path("assigneeName").asText()).isEqualTo("模拟MAINTAINER");
        assertThat(after.path("assignedBy")).isEqualTo(admin.account("dorm_manager").path("id"));
        assertThat(after.path("assignedAt").asText()).isNotBlank();
        assertThat(after.path("version").asLong()).isEqualTo(1);
        assertThat(after.path("logs").size()).isEqualTo(2);
        assertThat(after.path("logs").get(1).path("assigneeId").asLong()).isEqualTo(worker);
        assertThat(maintainer.get(path).statusCode()).isEqualTo(200);
        assertThat(image(maintainer, after.path("images").get(0).path("url").asText()).statusCode()).isEqualTo(200);
        assertThat(json.readTree(maintainer.get("/api/repairs?assigneeId=999999").body()).path("total").asInt()).isEqualTo(1);
        var unassigned = json.readTree(upload(student, form()).body());
        assertThat(maintainer.get("/api/repairs/" + unassigned.path("id").asLong()).statusCode()).isEqualTo(404);
        assertThat(student.postJson(path + "/assign", Map.of("assigneeId", worker, "version", 1)).statusCode()).isEqualTo(403);
        assertThat(student.get("/api/repairs/maintainers").statusCode()).isEqualTo(403);
        assertThat(manager.postJson(path + "/assign", Map.of("assigneeId", worker, "version", 0)).statusCode()).isEqualTo(409);
    }
    private Browser login(String name) throws Exception {
        var b = new Browser(); assertThat(b.login(name, PASSWORD).statusCode()).isEqualTo(200); return b;
    }
    private JsonNode create(Browser admin, String path, Object body) throws Exception {
        var response = admin.postJson("/api/" + path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201); return json.readTree(response.body());
    }
    private JsonNode moveIn(Browser admin) throws Exception {
        long user = studentAccount(admin).path("id").asLong();
        var student = create(admin, "housing/students", Map.of("userId", user, "studentNo", "TEST05", "fullName", "模拟报修学生", "college", "模拟学院", "major", "模拟专业", "className", "模拟班级"));
        var bed = bed(admin, "报修原楼栋");
        return create(admin, "housing/residencies", Map.of("studentId", student.path("id").asLong(), "bedId", bed.path("id").asLong()));
    }
    private JsonNode studentAccount(Browser admin) throws Exception {
        var matches = json.readTree(admin.get("/api/identity/accounts?q=student").body()).path("items");
        return java.util.stream.StreamSupport.stream(matches.spliterator(), false)
                .filter(row -> row.path("username").asText().equals("student")).findFirst().orElseThrow();
    }
    private JsonNode bed(Browser admin, String name) throws Exception {
        var building = create(admin, "housing/buildings", Map.of("name", name));
        var room = create(admin, "housing/rooms", Map.of("buildingId", building.path("id").asLong(), "roomNo", "101", "floor", 1));
        return create(admin, "housing/beds", Map.of("roomId", room.path("id").asLong(), "bedNo", "01"));
    }
    private record Upload(String name, String type, byte[] bytes) {}
    private Upload png() throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return new Upload("模拟现场.png", "image/png", out.toByteArray());
    }
    private Map<String, String> form() {
        return new LinkedHashMap<>(Map.of("requestId", UUID.randomUUID().toString(), "category", "PLUMBING", "description", "模拟水龙头持续滴水"));
    }
    private HttpResponse<String> upload(Browser b, Map<String, String> fields, Upload... images) throws Exception {
        String boundary = "testBoundary" + UUID.randomUUID(); var out = new ByteArrayOutputStream();
        for (var field : fields.entrySet()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (var image : images) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"" + image.name() + "\"\r\nContent-Type: " + image.type() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(image.bytes()); out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpClient.newBuilder().cookieHandler(b.cookies).build().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/repairs"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("X-CSRF-TOKEN", b.csrf())
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<byte[]> image(Browser b, String path) throws Exception {
        return HttpClient.newBuilder().cookieHandler(b.cookies).build().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void residentSubmitsActualImageAndOwnHistoryUsesServerIdentityAndLocation() throws Exception {
        var admin = login("system_admin"); var student = login("student");
        assertThat(upload(student, form()).statusCode()).isEqualTo(409);
        var residence = moveIn(admin); var fields = form();
        fields.put("studentId", "999999"); fields.put("roomId", "999999"); fields.put("submittedBy", "999999");
        var response = upload(student, fields, png());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var result = json.readTree(response.body());
        assertThat(result.path("status").asText()).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(result.path("roomId")).isEqualTo(residence.path("roomId"));
        assertThat(result.path("buildingName").asText()).isEqualTo("报修原楼栋");
        assertThat(result.path("submittedBy")).isEqualTo(admin.account("student").path("id"));
        assertThat(result.path("images").size()).isEqualTo(1);
        assertThat(result.path("logs").size()).isEqualTo(1);
        assertThat(result.path("logs").get(0).path("toStatus").asText()).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(result.path("logs").get(0).path("actorName").asText()).isEqualTo("模拟STUDENT");
        var content = image(student, result.path("images").get(0).path("url").asText());
        assertThat(content.statusCode()).isEqualTo(200);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(content.body())).getWidth()).isEqualTo(8);
        assertThat(content.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain("storageKey", ".runtime", "password");
        assertThat(json.readTree(student.get("/api/repairs?submittedBy=999999").body()).path("total").asInt()).isEqualTo(1);
        assertThat(json.readTree(admin.get("/api/repairs").body()).path("total").asInt()).isEqualTo(1);
    }
    @Test
    void cancellationRequiresReasonAndPreservesHistoryWhileUnfinishedOrdersBlockDeactivation() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var residence = moveIn(admin);
        var response = upload(student, form(), png()); assertThat(response.statusCode()).isEqualTo(201);
        var order = json.readTree(response.body()); long id = order.path("id").asLong();
        assertThat(admin.postJson("/api/housing/residencies/" + residence.path("id").asLong() + "/checkout", Map.of("note", "模拟退宿")).statusCode()).isEqualTo(200);
        var paths = List.of("/api/identity/accounts/" + admin.account("student").path("id").asLong(),
                "/api/housing/students/" + residence.path("studentId").asLong(), "/api/housing/buildings/" + residence.path("buildingId").asLong(),
                "/api/housing/rooms/" + residence.path("roomId").asLong(), "/api/housing/beds/" + residence.path("bedId").asLong());
        for (var path : paths) {
            var denied = admin.postJson(path + "/enabled", Map.of("enabled", false, "version", 0));
            assertThat(denied.statusCode()).as(path + denied.body()).isEqualTo(409);
            assertThat(denied.body()).contains("未完工单");
        }
        assertThat(student.postJson("/api/repairs/" + id + "/cancel", Map.of("reason", " ")).statusCode()).isEqualTo(400);
        var cancelled = student.postJson("/api/repairs/" + id + "/cancel", Map.of("reason", "模拟误报，问题已解决"));
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
        var after = json.readTree(cancelled.body());
        assertThat(after.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(after.path("logs").size()).isEqualTo(2);
        assertThat(after.path("logs").get(1).path("note").asText()).isEqualTo("模拟误报，问题已解决");
        assertThat(student.postJson("/api/repairs/" + id + "/cancel", Map.of("reason", "重放请求")).statusCode()).isEqualTo(409);
        assertThat(image(student, after.path("images").get(0).path("url").asText()).statusCode()).isEqualTo(200);
        for (var path : paths) assertThat(admin.postJson(path + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(admin.get("/api/repairs/" + id).body()).path("logs").size()).isEqualTo(2);
    }
    @Test
    void historyAndImagesSurviveTransferAndCheckoutButOtherStudentsAndUnassignedMaintainersCannotRead() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var manager = login("dorm_manager"); var maintainer = login("maintainer");
        create(admin, "identity/accounts", Map.of("username", "other_student", "displayName", "模拟乙", "role", "STUDENT", "password", PASSWORD));
        var other = login("other_student"); var r = moveIn(admin);
        var old = json.readTree(upload(student, form(), png()).body()); String oldPath = "/api/repairs/" + old.path("id").asLong();
        String url = old.path("images").get(0).path("url").asText();
        assertThat(other.get(oldPath).statusCode()).isEqualTo(404);
        assertThat(image(other, url).statusCode()).isEqualTo(404);
        assertThat(other.postJson(oldPath + "/cancel", Map.of("reason", "越权取消")).statusCode()).isEqualTo(404);
        assertThat(json.readTree(other.get("/api/repairs?submittedBy=" + old.path("submittedBy").asLong()).body()).path("total").asInt()).isZero();
        assertThat(maintainer.get(oldPath).statusCode()).isEqualTo(404);
        assertThat(image(maintainer, url).statusCode()).isEqualTo(404);
        assertThat(new Browser().get(oldPath).statusCode()).isEqualTo(401);
        assertThat(image(new Browser(), url).statusCode()).isEqualTo(401);
        assertThat(image(manager, url).statusCode()).isEqualTo(200);
        assertThat(image(admin, url).statusCode()).isEqualTo(200);
        assertThat(image(student, url.replace("/images/", "0/images/")).statusCode()).isEqualTo(404);
        for (String path : List.of("/uploads/demo/anything.png", "/.runtime/uploads/demo/anything.png", "/api/repairs/images/anything.png")) {
            assertThat(image(student, path).statusCode()).isIn(401, 403); // Session cookie intentionally has /api scope.
        }
        var next = bed(admin, "报修新楼栋");
        var movedResponse = admin.postJson("/api/housing/residencies/" + r.path("id").asLong() + "/transfer", Map.of("bedId", next.path("id").asLong(), "note", "模拟调寝"));
        assertThat(movedResponse.statusCode()).isEqualTo(200); var moved = json.readTree(movedResponse.body());
        var fresh = json.readTree(upload(student, form()).body());
        assertThat(fresh.path("buildingName").asText()).isEqualTo("报修新楼栋");
        assertThat(json.readTree(student.get(oldPath).body()).path("buildingName").asText()).isEqualTo("报修原楼栋");
        assertThat(admin.postJson("/api/housing/buildings/" + r.path("buildingId").asLong(), Map.of("name", "原楼更名", "version", 0)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(student.get(oldPath).body()).path("buildingName").asText()).isEqualTo("报修原楼栋");
        assertThat(admin.postJson("/api/housing/residencies/" + moved.path("id").asLong() + "/checkout", Map.of("note", "模拟退宿")).statusCode()).isEqualTo(200);
        assertThat(upload(student, form()).statusCode()).isEqualTo(409);
        assertThat(student.get(oldPath).statusCode()).isEqualTo(200);
        assertThat(image(student, url).statusCode()).isEqualTo(200);
        assertThat(manager.postJson(oldPath + "/cancel", Map.of("reason", "宿管取消误报")).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/repairs/" + fresh.path("id").asLong() + "/cancel", Map.of("reason", "管理员取消误报")).statusCode()).isEqualTo(200);
        var secondPage = json.readTree(student.get("/api/repairs?status=CANCELLED&page=2&size=1").body());
        assertThat(secondPage.path("total").asInt()).isEqualTo(2); assertThat(secondPage.path("items").get(0).path("id")).isEqualTo(old.path("id"));
        assertThat(json.readTree(student.get("/api/repairs?q=missing").body()).path("total").asInt()).isZero();
    }

    @Test
    void validatesImageContentTypesSizesCountsAndInputs() throws Exception {
        var admin = login("system_admin"); var student = login("student"); moveIn(admin);
        var jpeg = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        byte[] webp = Base64.getDecoder().decode("UklGRhYCAABXRUJQVlA4WAoAAAAgAAAABwAABwAASUNDUMgBAAAAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADZWUDggKAAAAJABAJ0BKggACAABQCYliAJ0ugADmAD+8iJftTYqp53fxuyzhukAAAA=");
        var valid = upload(student, form(), png(), new Upload("photo.jpg", "image/jpeg", jpeg.toByteArray()), new Upload("photo.webp", "image/webp", webp), png(), png());
        assertThat(valid.statusCode()).as(valid.body()).isEqualTo(201);
        var detail = json.readTree(valid.body()); assertThat(detail.path("images").size()).isEqualTo(5);
        for (var item : detail.path("images")) assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image(student, item.path("url").asText()).body())).getWidth()).isEqualTo(8);
        var gif = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "gif", gif);
        for (var invalid : List.of(new Upload("fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8)),
                new Upload("wrong.jpg", "image/jpeg", png().bytes()), new Upload("x.gif", "image/gif", gif.toByteArray()),
                new Upload("empty.png", "image/png", new byte[0]), new Upload("truncated.png", "image/png", Arrays.copyOf(png().bytes(), 35)),
                new Upload("large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))) {
            var denied = upload(student, form(), invalid); assertThat(denied.statusCode()).as(invalid.name() + denied.body()).isEqualTo(400);
            assertThat(denied.body()).contains("INVALID_IMAGE");
        }
        assertThat(upload(student, form(), png(), png(), png(), png(), png(), png()).statusCode()).isEqualTo(400);
        assertThat(upload(student, form(), new Upload("huge.png", "image/png", new byte[7 * 1024 * 1024])).statusCode()).isEqualTo(413);
        for (var entry : Map.of("description", " ", "category", "FAKE", "requestId", "bad").entrySet()) {
            var fields = form(); fields.put(entry.getKey(), entry.getValue()); assertThat(upload(student, fields).statusCode()).isEqualTo(400);
        }
        var tooLong = form(); tooLong.put("description", "字".repeat(2001)); assertThat(upload(student, tooLong).statusCode()).isEqualTo(400);
        var missing = form(); missing.remove("category"); assertThat(upload(student, missing).statusCode()).isEqualTo(400);
        for (String query : List.of("page=0", "size=101", "status=FAKE", "q=" + "x".repeat(65))) assertThat(student.get("/api/repairs?" + query).statusCode()).isEqualTo(400);
        assertThat(upload(admin, form()).statusCode()).isEqualTo(403);
        assertThat(student.send("/api/repairs", "POST", "multipart/form-data; boundary=x", "--x--", null).statusCode()).isEqualTo(403);
        assertThat(student.send("/api/repairs/" + detail.path("id").asLong(), "DELETE", null, null, student.csrf()).statusCode()).isEqualTo(403);
        assertThat(json.readTree(student.get("/api/repairs").body()).path("total").asInt()).isEqualTo(1);
    }

    @Test
    void retriesAndConcurrentCancellationProduceOneOrderAndOneTransition() throws Exception {
        var admin = login("system_admin"); var student = login("student"); moveIn(admin); var fields = form(); var file = png();
        student.csrf(); // Initialize the post-login session token before simultaneous requests.
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> upload(student, fields, file)); var second = pool.submit(() -> upload(student, fields, file));
            var a = first.get(); var b = second.get(); assertThat(a.statusCode()).isEqualTo(201); assertThat(b.statusCode()).isEqualTo(201);
            var order = json.readTree(a.body()); assertThat(json.readTree(b.body()).path("id")).isEqualTo(order.path("id"));
            fields.put("description", "不同的内容"); assertThat(upload(student, fields, file).statusCode()).isEqualTo(409);
            String path = "/api/repairs/" + order.path("id").asLong();
            var studentCancel = pool.submit(() -> student.postJson(path + "/cancel", Map.of("reason", "学生取消")));
            var adminCancel = pool.submit(() -> admin.postJson(path + "/cancel", Map.of("reason", "管理员取消")));
            assertThat(List.of(studentCancel.get().statusCode(), adminCancel.get().statusCode())).containsExactlyInAnyOrder(200, 409);
            var after = json.readTree(student.get(path).body()); assertThat(after.path("logs").size()).isEqualTo(2);
            assertThat(after.path("images").size()).isEqualTo(1);
            assertThat(json.readTree(student.get("/api/repairs").body()).path("total").asInt()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    private long fileCount(java.nio.file.Path root) throws Exception {
        if (!java.nio.file.Files.exists(root)) return 0;
        try (var files = java.nio.file.Files.list(root)) { return files.count(); }
    }
    @Test
    void fileAndDatabaseFailuresLeaveNoPartialOrderAssociationOrLogAndRetryRecovers() throws Exception {
        var admin = login("system_admin"); var student = login("student"); moveIn(admin);
        var root = java.nio.file.Path.of("../.runtime/uploads/test-api").toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(root); long baseline = fileCount(root);
        // Only this dedicated API-test directory is moved; demo/browser storage is untouched.
        var parked = root.resolveSibling("test-api-parked-" + UUID.randomUUID());
        java.nio.file.Files.move(root, parked);
        try {
            java.nio.file.Files.writeString(root, "simulate unavailable directory");
            assertThat(upload(student, form(), png()).statusCode()).isEqualTo(500);
            assertThat(json.readTree(student.get("/api/repairs").body()).path("total").asInt()).isZero();
        } finally { java.nio.file.Files.deleteIfExists(root); java.nio.file.Files.move(parked, root); }
        for (String table : List.of("repair_image", "repair_status_log", "operation_log")) {
            String check = switch (table) {
                case "repair_image" -> "byte_size < 0";
                case "repair_status_log" -> "note <> '提交报修'";
                default -> "action <> 'REPAIR_SUBMITTED'";
            };
            long audits = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class);
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT test_reject_repair CHECK (" + check + ")");
            var fields = form();
            try {
                var failed = upload(student, fields, png(), png());
                assertThat(failed.statusCode()).as(failed.body()).isEqualTo(500);
                assertThat(failed.body()).doesNotContain("SQL", "Exception", "test_reject", ".runtime");
                assertThat(json.readTree(student.get("/api/repairs").body()).path("total").asInt()).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repair_image", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repair_status_log", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isEqualTo(audits);
                assertThat(fileCount(root)).isEqualTo(baseline);
            } finally { jdbc.execute("ALTER TABLE " + table + " DROP CHECK test_reject_repair"); }
        }
        var fields = form(); var response = upload(student, fields, png());
        assertThat(response.statusCode()).isEqualTo(201); var order = json.readTree(response.body()); long id = order.path("id").asLong();
        assertThat(fileCount(root)).isEqualTo(baseline + 1);
        for (String table : List.of("repair_status_log", "operation_log")) {
            String check = table.equals("repair_status_log") ? "to_status <> 'CANCELLED'" : "action <> 'REPAIR_CANCELLED'";
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT test_reject_cancel CHECK (" + check + ")");
            try {
                assertThat(student.postJson("/api/repairs/" + id + "/cancel", Map.of("reason", "模拟故障")).statusCode()).isEqualTo(500);
                assertThat(json.readTree(student.get("/api/repairs/" + id).body())).isEqualTo(order);
            } finally { jdbc.execute("ALTER TABLE " + table + " DROP CHECK test_reject_cancel"); }
        }
        assertThat(student.postJson("/api/repairs/" + id + "/cancel", Map.of("reason", "故障恢复重试")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'REPAIR_SUBMITTED' AND actor_id = ? AND target_id = ?", Long.class, order.path("submittedBy").asLong(), id)).isEqualTo(1);
    }

    @Test
    void concurrentTransferOrCheckoutTakesAConsistentSubmissionLocationAndProtectsOpenReferences() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var residence = moveIn(admin); var nextBed = bed(admin, "竞争调寝楼栋");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var submit = pool.submit(() -> upload(student, form()));
            var transfer = pool.submit(() -> admin.postJson("/api/housing/residencies/" + residence.path("id").asLong() + "/transfer", Map.of("bedId", nextBed.path("id").asLong(), "note", "竞争调寝")));
            var created = submit.get(); var transferred = transfer.get();
            assertThat(created.statusCode()).isEqualTo(201); assertThat(transferred.statusCode()).isEqualTo(200);
            var order = json.readTree(created.body()); var moved = json.readTree(transferred.body());
            var expected = order.path("residencyId").equals(residence.path("id")) ? residence : moved;
            assertThat(order.path("residencyId")).isEqualTo(expected.path("id"));
            assertThat(order.path("bedId")).isEqualTo(expected.path("bedId")); assertThat(order.path("buildingName")).isEqualTo(expected.path("buildingName"));
            var submitAgain = pool.submit(() -> upload(student, form()));
            var checkout = pool.submit(() -> admin.postJson("/api/housing/residencies/" + moved.path("id").asLong() + "/checkout", Map.of("note", "竞争退宿")));
            var submission = submitAgain.get(); assertThat(submission.statusCode()).isIn(201, 409); assertThat(checkout.get().statusCode()).isEqualTo(200);
            if (submission.statusCode() == 201) assertThat(json.readTree(submission.body()).path("residencyId")).isEqualTo(moved.path("id"));
            assertThat(admin.postJson("/api/housing/beds/" + order.path("bedId").asLong() + "/enabled", Map.of("enabled", false, "version", 0)).statusCode()).isEqualTo(409);
            assertThat(student.get("/api/repairs/" + order.path("id").asLong()).statusCode()).isEqualTo(200);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void imageAuthorizationIsRecheckedAfterSessionRevocationAndCancelledHistoryIsRetained() throws Exception {
        var admin = login("system_admin"); var student = login("student"); var residence = moveIn(admin);
        var order = json.readTree(upload(student, form(), png()).body()); String url = order.path("images").get(0).path("url").asText();
        assertThat(image(student, url).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/repairs/" + order.path("id").asLong() + "/cancel", Map.of("reason", "模拟取消")).statusCode()).isEqualTo(200);
        assertThat(admin.postJson("/api/housing/residencies/" + residence.path("id").asLong() + "/checkout", Map.of("note", "退宿")).statusCode()).isEqualTo(200);
        var account = admin.account("student");
        assertThat(admin.postJson("/api/identity/accounts/" + account.path("id").asLong() + "/enabled", Map.of("enabled", false, "version", account.path("version").asLong())).statusCode()).isEqualTo(200);
        assertThat(image(student, url).statusCode()).isEqualTo(401);
        assertThat(image(admin, url).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM repair_order", Long.class)).isEqualTo(1);
    }
}
