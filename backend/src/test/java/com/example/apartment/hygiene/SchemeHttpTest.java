package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SchemeHttpTest extends HttpIntegrationTest {
    @Test
    void onlyAdministratorsWriteAndManagersReadPublishedVersionsWithLiveSessionChecks() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager"); var student = login("student"); var worker = login("maintainer");
        assertThat(new Browser().get(BASE).statusCode()).isEqualTo(401);
        var draft = create(admin,"模拟角色方案","100"); long id=draft.path("id").asLong();
        for(var actor:List.of(manager,student,worker)) {
            assertThat(actor.postJson(BASE,definition("越权创建","100")).statusCode()).isEqualTo(403);
            assertThat(actor.postJson(BASE+"/"+id,Map.of("definition",definition("越权编辑","100"),"version",0)).statusCode()).isEqualTo(403);
            assertThat(actor.postJson(BASE+"/"+id+"/copy",Map.of("version",0)).statusCode()).isEqualTo(403);
            assertThat(actor.postJson(BASE+"/"+id+"/activate",Map.of("version",0,"activeId",0)).statusCode()).isEqualTo(403);
        }
        for(var actor:List.of(student,worker)) {
            assertThat(actor.get(BASE).statusCode()).isEqualTo(403);
            assertThat(actor.get(BASE+"/"+id).statusCode()).isEqualTo(403);
        }
        assertThat(manager.get(BASE+"/"+id).statusCode()).isEqualTo(404);
        assertThat(json.readTree(manager.get(BASE+"?publishedOnly=false").body()).path("total").asInt()).isZero();
        assertThat(admin.send(BASE,"POST","application/json",json.writeValueAsString(definition("无CSRF","100")),null).statusCode()).isEqualTo(403);
        assertThat(admin.send(BASE+"/"+id,"DELETE",null,null,admin.csrf()).statusCode()).isEqualTo(403);
        assertThat(admin.postJson(BASE+"/"+id+"/activate",Map.of("version",0,"activeId",0)).statusCode()).isEqualTo(200);
        assertThat(manager.get(BASE+"/"+id).statusCode()).isEqualTo(200);
        var account=admin.account("dorm_manager");
        assertThat(admin.postJson("/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("enabled",false,"version",0)).statusCode()).isEqualTo(200);
        assertThat(manager.get(BASE).statusCode()).isEqualTo(401);
        assertThat(detail(admin,id).path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void concurrentActivationAndEditingCannotOverwriteAnotherDecisionOrProduceTwoActiveVersions() throws Exception {
        var admin=login("system_admin"); admin.csrf();
        var first=create(admin,"模拟竞争甲","100"); var second=create(admin,"模拟竞争乙","100");
        long firstId=first.path("id").asLong(),secondId=second.path("id").asLong();
        var gate=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{gate.await();return admin.postJson(BASE+"/"+firstId+"/activate",Map.of("version",0,"activeId",0));});
            var b=pool.submit(()->{gate.await();return admin.postJson(BASE+"/"+secondId+"/activate",Map.of("version",0,"activeId",0));});
            gate.countDown();assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        var list=json.readTree(admin.get(BASE+"?status=ACTIVE").body());assertThat(list.path("total").asInt()).isEqualTo(1);
        long activeId=list.path("active").path("id").asLong();
        long draftId=activeId==firstId?secondId:firstId;
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("UPDATE hygiene_scheme SET status='ACTIVE' WHERE id=?",draftId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var ready=new java.util.concurrent.CountDownLatch(1);var racers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var edit=racers.submit(()->{ready.await();return admin.postJson(BASE+"/"+draftId,Map.of("definition",definition("模拟修改后的草稿","40","30","30"),"version",0));});
            var activate=racers.submit(()->{ready.await();return admin.postJson(BASE+"/"+draftId+"/activate",Map.of("version",0,"activeId",activeId));});
            ready.countDown();var e=edit.get();var a=activate.get();assertThat(List.of(e.statusCode(),a.statusCode())).containsExactlyInAnyOrder(200,409);
            var after=detail(admin,draftId);assertThat(after.path("version").asLong()).isEqualTo(1);
            if(e.statusCode()==200){assertThat(after.path("status").asText()).isEqualTo("DRAFT");assertThat(after.path("name").asText()).isEqualTo("模拟修改后的草稿");}
            else {assertThat(after.path("status").asText()).isEqualTo("ACTIVE");assertThat(after.path("name").asText()).isIn("模拟竞争甲","模拟竞争乙");}
        } finally {racers.shutdownNow();}
        assertThat(json.readTree(admin.get(BASE+"?status=ACTIVE").body()).path("total").asInt()).isEqualTo(1);
    }

    @Test
    void failuresRollbackDraftDimensionsCopyAndBothSidesOfActivationIncludingAudits() throws Exception {
        var admin=login("system_admin");
        for(String table:List.of("hygiene_scheme_dimension","operation_log")) {
            String predicate=table.equals("operation_log")?"action <> 'SCHEME_CREATED'":"name <> '摆放'";
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_scheme CHECK ("+predicate+")");
            try {
                var response=admin.postJson(BASE,definition("模拟创建失败","40","30","30"));
                assertThat(response.statusCode()).isEqualTo(500);assertThat(response.body()).doesNotContain("SQL","Exception","test_reject");
                assertThat(json.readTree(admin.get(BASE).body()).path("total").asInt()).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_scheme_dimension",Long.class)).isZero();
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_scheme");}
        }
        var original=create(admin,"模拟原标准","100");long id=original.path("id").asLong();
        for(String table:List.of("hygiene_scheme_dimension","operation_log")) {
            String predicate=table.equals("operation_log")?"action <> 'SCHEME_UPDATED'":"name <> '摆放'";
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_scheme CHECK ("+predicate+")");
            try {
                assertThat(admin.postJson(BASE+"/"+id,Map.of("definition",definition("模拟编辑失败","40","30","30"),"version",0)).statusCode()).isEqualTo(500);
                assertThat(detail(admin,id)).isEqualTo(original);
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_scheme");}
        }
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_reject_scheme CHECK (action <> 'SCHEME_COPIED')");
        try {
            assertThat(admin.postJson(BASE+"/"+id+"/copy",Map.of("version",0)).statusCode()).isEqualTo(500);
            assertThat(json.readTree(admin.get(BASE).body()).path("total").asInt()).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE operation_log DROP CHECK test_reject_scheme");}
        assertThat(admin.postJson(BASE+"/"+id+"/activate",Map.of("version",0,"activeId",0)).statusCode()).isEqualTo(200);
        var next=create(admin,"模拟新标准","40","30","30");long nextId=next.path("id").asLong();var previous=detail(admin,id);
        long audits=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
        for(String failure:List.of("row","archiveAudit","activateAudit")) {
            String table=failure.equals("row")?"hygiene_scheme":"operation_log";
            String predicate=switch(failure){case "row"->"name <> '模拟新标准' OR status <> 'ACTIVE'";case "archiveAudit"->"action <> 'SCHEME_ARCHIVED'";default->"action <> 'SCHEME_ACTIVATED' OR target_id <> "+nextId;};
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_scheme CHECK ("+predicate+")");
            try {
                assertThat(admin.postJson(BASE+"/"+nextId+"/activate",Map.of("version",0,"activeId",id)).statusCode()).isEqualTo(500);
                assertThat(detail(admin,id)).isEqualTo(previous);assertThat(detail(admin,nextId)).isEqualTo(next);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class)).isEqualTo(audits);
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_scheme");}
        }
        assertThat(admin.postJson(BASE+"/"+nextId+"/activate",Map.of("version",0,"activeId",id)).statusCode()).isEqualTo(200);
        var audit=jdbc.queryForMap("SELECT actor_id,before_data,after_data FROM operation_log WHERE action='SCHEME_ACTIVATED' AND target_id=?",nextId);
        assertThat(((Number)audit.get("actor_id")).longValue()).isEqualTo(admin.account("system_admin").path("id").asLong());
        assertThat(audit.get("before_data").toString()).contains("DRAFT");assertThat(audit.get("after_data").toString()).contains("ACTIVE","dimensions").doesNotContain("password");
    }

    @Test
    void filteredPaginationAndReadOnlyQueriesPreserveDraftsAndPublishedHistory() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        for(int i=0;i<12;i++)create(admin,"模拟分页方案"+i,"100");
        long audits=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
        var first=json.readTree(admin.get(BASE+"?q="+java.net.URLEncoder.encode("模拟分页方案",java.nio.charset.StandardCharsets.UTF_8)+"&size=10").body());
        assertThat(first.path("total").asInt()).isEqualTo(12);assertThat(first.path("items").size()).isEqualTo(10);
        var second=json.readTree(admin.get(BASE+"?page=2&size=10").body());
        assertThat(second.path("items").size()).isEqualTo(2);assertThat(second.path("items").get(1).path("name").asText()).isEqualTo("模拟分页方案0");
        assertThat(json.readTree(admin.get(BASE+"?q=absent").body()).path("total").asInt()).isZero();
        assertThat(json.readTree(manager.get(BASE).body()).path("total").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class)).isEqualTo(audits);
    }
    @Test
    void invalidWeightsThresholdsDimensionsAndVersionsAreRejectedWithoutPartialDrafts() throws Exception {
        var admin = login("system_admin");
        for (String weight : List.of("-0.01", "100.01", "33.333", "40.000")) {
            var response = admin.postJson(BASE, definition("模拟非法权重", weight));
            assertThat(response.statusCode()).isEqualTo(400); assertThat(response.body()).contains("INVALID_WEIGHT");
        }
        for (String threshold : List.of("-1", "100.01", "60.001")) {
            var data = definition("模拟非法阈值", "100"); data.put("threshold", new java.math.BigDecimal(threshold));
            var response = admin.postJson(BASE, data);
            assertThat(response.statusCode()).isEqualTo(400); assertThat(response.body()).contains("INVALID_THRESHOLD");
        }
        var duplicate = definition("重复维度", "50", "50");
        duplicate.put("dimensions", List.of(Map.of("name", "地面", "weight", 50), Map.of("name", " 地面 ", "weight", 50)));
        assertThat(admin.postJson(BASE, duplicate).body()).contains("DUPLICATE_DIMENSION");
        for (Object invalid : List.of(Map.of(), Map.of("name", " ", "dimensions", List.of(Map.of("name", "地面", "weight", 100))),
                Map.of("name", "x".repeat(65), "dimensions", List.of(Map.of("name", "地面", "weight", 100))),
                Map.of("name", "缺少维度", "dimensions", List.of()),
                Map.of("name", "空白维度", "dimensions", List.of(Map.of("name", " ", "weight", 100))),
                Map.of("name", "缺少权重", "dimensions", List.of(Map.of("name", "地面"))),
                Map.of("name", "超出数量", "dimensions", java.util.stream.IntStream.range(0,21).mapToObj(i -> Map.of("name", "维度"+i, "weight", 1)).toList()))) {
            assertThat(admin.postJson(BASE, invalid).statusCode()).isEqualTo(400);
        }
        assertThat(json.readTree(admin.get(BASE).body()).path("total").asInt()).isZero();
        var draft = create(admin, "模拟边界值", "0", "100"); long id = draft.path("id").asLong();
        for (Object version : List.of(-1, 0.5, "bad")) {
            assertThat(admin.postJson(BASE+"/"+id, Map.of("definition", definition("覆盖", "100"), "version", version)).statusCode()).isEqualTo(400);
        }
        assertThat(admin.postJson(BASE+"/"+id+"/activate", Map.of("version", 0)).statusCode()).isEqualTo(400);
        for (String query : List.of("page=0", "size=101", "status=UNKNOWN", "q="+"x".repeat(65))) assertThat(admin.get(BASE+"?"+query).statusCode()).isEqualTo(400);
        assertThat(detail(admin,id)).isEqualTo(draft);
    }
    @Test
    void activationRequiresExactlyOneHundredAndCopyPreservesPublishedHistory() throws Exception {
        var admin = login("system_admin"); var manager = login("dorm_manager");
        var invalid = create(admin, "模拟不足100", "33.33", "33.33", "33.33");
        var rejected = admin.postJson(BASE + "/" + invalid.path("id").asLong() + "/activate", Map.of("version", 0, "activeId", 0));
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(400);
        assertThat(rejected.body()).contains("WEIGHT_TOTAL");
        assertThat(json.readTree(manager.get(BASE).body()).path("active").isNull()).isTrue();
        var original = create(admin, "模拟正式方案", "40", "30", "30"); long id = original.path("id").asLong();
        var response = admin.postJson(BASE + "/" + id + "/activate", Map.of("version", 0, "activeId", 0));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var active = json.readTree(response.body());
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(active.path("activatedAt").isNull()).isFalse();
        assertThat(active.path("version").asLong()).isEqualTo(1);
        assertThat(detail(manager, id)).isEqualTo(active);
        assertThat(admin.postJson(BASE + "/" + id, Map.of("definition", definition("改写已启用", "50", "30", "20"), "version", 1)).statusCode()).isEqualTo(409);
        var copiedResponse = admin.postJson(BASE + "/" + id + "/copy", Map.of("version", 1));
        assertThat(copiedResponse.statusCode()).as(copiedResponse.body()).isEqualTo(201);
        var copied = json.readTree(copiedResponse.body()); long nextId = copied.path("id").asLong();
        assertThat(nextId).isNotEqualTo(id); assertThat(copied.path("sourceId").asLong()).isEqualTo(id);
        assertThat(copied.path("status").asText()).isEqualTo("DRAFT");
        assertThat(copied.path("threshold")).isEqualTo(active.path("threshold"));
        var updated = definition("模拟调整版", "33.33", "33.33", "33.34"); updated.put("threshold", 70);
        assertThat(admin.postJson(BASE + "/" + nextId, Map.of("definition", updated, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(admin.postJson(BASE + "/" + nextId + "/activate", Map.of("version", 1, "activeId", 0)).statusCode()).isEqualTo(409);
        assertThat(detail(admin, id)).isEqualTo(active);
        assertThat(admin.postJson(BASE + "/" + nextId + "/activate", Map.of("version", 1, "activeId", id)).statusCode()).isEqualTo(200);
        var archived = detail(manager, id);
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        assertThat(archived.path("dimensions")).isEqualTo(active.path("dimensions"));
        assertThat(archived.path("threshold")).isEqualTo(active.path("threshold"));
        assertThat(archived.path("activatedAt")).isEqualTo(active.path("activatedAt"));
        assertThat(admin.postJson(BASE + "/" + id + "/activate", Map.of("version", 2, "activeId", nextId)).statusCode()).isEqualTo(409);
        assertThat(admin.postJson(BASE + "/" + id, Map.of("definition", updated, "version", 2)).statusCode()).isEqualTo(409);
        var listed = json.readTree(manager.get(BASE + "?size=1").body());
        assertThat(listed.path("total").asInt()).isEqualTo(2);
        assertThat(listed.path("active").path("id").asLong()).isEqualTo(nextId);
        assertThat(json.readTree(manager.get(BASE + "?status=DRAFT").body()).path("total").asInt()).isZero();
        assertThat(manager.get(BASE + "/" + invalid.path("id").asLong()).statusCode()).isEqualTo(404);
    }
    private static final String BASE = "/api/hygiene/schemes";
    private Browser login(String name) throws Exception {
        var b = new Browser(); assertThat(b.login(name, PASSWORD).statusCode()).isEqualTo(200); return b;
    }
    private Map<String, Object> definition(String name, String... weights) {
        var dimensions = new ArrayList<Map<String, Object>>();
        String[] names = {"地面", "摆放", "整洁"};
        for (int i = 0; i < weights.length; i++) dimensions.add(Map.of("name", names[i], "weight", new java.math.BigDecimal(weights[i])));
        return new LinkedHashMap<>(Map.of("name", name, "dimensions", dimensions));
    }
    private JsonNode create(Browser admin, String name, String... weights) throws Exception {
        var response = admin.postJson(BASE, definition(name, weights));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201); return json.readTree(response.body());
    }
    private JsonNode detail(Browser browser, long id) throws Exception {
        var response = browser.get(BASE + "/" + id);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return json.readTree(response.body());
    }
    @Test
    void administratorCreatesAndEditsDraftWithDefaultThresholdAndOrderedDimensions() throws Exception {
        var admin = login("system_admin");
        var draft = create(admin, "模拟卫生方案", "40", "30", "30");
        long id = draft.path("id").asLong();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("threshold").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(draft.path("totalWeight").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(draft.path("dimensionCount").asInt()).isEqualTo(3);
        assertThat(draft.path("dimensions").get(0).path("name").asText()).isEqualTo("地面");
        assertThat(draft.path("dimensions").get(2).path("weight").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(detail(admin, id)).isEqualTo(draft);
        var body = definition("模拟方案修订", "33.33", "33.33", "33.33"); body.put("threshold", 65.5);
        var edited = admin.postJson(BASE + "/" + id, Map.of("definition", body, "version", 0));
        assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
        var result = json.readTree(edited.body());
        assertThat(result.path("totalWeight").decimalValue()).isEqualByComparingTo("99.99");
        assertThat(result.path("threshold").decimalValue()).isEqualByComparingTo("65.50");
        assertThat(result.path("version").asLong()).isEqualTo(1);
        assertThat(admin.postJson(BASE + "/" + id, Map.of("definition", body, "version", 0)).statusCode()).isEqualTo(409);
        assertThat(json.readTree(admin.get(BASE).body()).path("total").asInt()).isEqualTo(1);
    }
}
