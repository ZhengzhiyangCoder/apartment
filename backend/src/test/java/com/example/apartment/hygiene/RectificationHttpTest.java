package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class RectificationHttpTest extends HttpIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired BatchHttpTest.MutableClock time;
    @org.junit.jupiter.api.BeforeEach void resetTime() { time.at("2026-11-16T01:00:00Z"); }
    private static final String EVENTS="/api/hygiene/rectifications";
    private Browser login(String name) throws Exception { var b=new Browser();assertThat(b.login(name,PASSWORD).statusCode()).isEqualTo(200);return b; }
    private JsonNode get(Browser b,String path) throws Exception { var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body()); }
    private JsonNode post(Browser b,String path,Object body,int status) throws Exception { var r=b.postJson(path,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body()); }
    private record Fixture(Browser admin,Browser manager,long building,long room,long residency,JsonNode batch) {}
    private Fixture fixture() throws Exception {
        var a=login("system_admin");var m=login("dorm_manager");
        long building=post(a,"/api/housing/buildings",Map.of("name","模拟整改楼"),201).path("id").asLong();
        long room=post(a,"/api/housing/rooms",Map.of("buildingId",building,"roomNo","101","floor",1),201).path("id").asLong();
        long bed=post(a,"/api/housing/beds",Map.of("roomId",room,"bedNo","01"),201).path("id").asLong();
        long student=post(a,"/api/housing/students",Map.of("userId",a.account("student").path("id").asLong(),"studentNo","rectification_student","fullName","模拟学生","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        long residency=post(a,"/api/housing/residencies",Map.of("studentId",student,"bedId",bed),201).path("id").asLong();
        var scheme=post(a,"/api/hygiene/schemes",Map.of("name","模拟临界标准","dimensions",List.of(Map.of("name","主要维度","weight",99),Map.of("name","次要维度","weight",1))),201);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        var batch=post(m,"/api/hygiene/batches/current/prepare",Map.of(),200);
        return new Fixture(a,m,building,room,residency,batch);
    }
    private Map<String,Object> scores(JsonNode batch,int first,int second) {
        return Map.of("dimensions",List.of(Map.of("dimensionId",batch.path("dimensions").get(0).path("id").asLong(),"score",first),Map.of("dimensionId",batch.path("dimensions").get(1).path("id").asLong(),"score",second)));
    }
    private String scorePath(Fixture f,JsonNode batch) { return "/api/hygiene/batches/"+batch.path("id").asLong()+"/rooms/"+f.room+"/score"; }
    private JsonNode next(Fixture f,String instant) throws Exception { time.at(instant);return post(f.manager,"/api/hygiene/batches/current/prepare",Map.of(),200); }
    private JsonNode only(Fixture f) throws Exception { var list=get(f.manager,EVENTS);assertThat(list.path("total").asInt()).isEqualTo(1);return get(f.manager,EVENTS+"/"+list.path("items").get(0).path("id").asLong()); }

    @Test void strictSingleScoreThresholdCreatesOneEventAndKeepsOriginalBasis() throws Exception {
        var f=fixture();var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,60,59),201);
        assertThat(score.path("totalScore").decimalValue()).isEqualByComparingTo("59.99");
        var event=only(f);assertThat(event.path("status").asText()).isEqualTo("OPEN");
        assertThat(event.path("triggers").size()).isEqualTo(1);
        var trigger=event.path("triggers").get(0);
        assertThat(trigger.path("scoreId")).isEqualTo(score.path("id"));
        assertThat(trigger.path("batchId")).isEqualTo(f.batch.path("id"));
        assertThat(trigger.path("totalScore").decimalValue()).isEqualByComparingTo("59.99");
        assertThat(trigger.path("threshold").decimalValue()).isEqualByComparingTo("60.00");
        var w2=next(f,"2026-11-23T01:00:00Z");post(f.manager,scorePath(f,w2),scores(w2,60,60),201);
        ((com.fasterxml.jackson.databind.node.ObjectNode)trigger).put("batchStatus","CLOSED"); // Preparing a later week expires the previous batch.
        assertThat(only(f)).isEqualTo(event); // A later pass never closes or appends a low-score trigger.
        var scheme=post(f.admin,"/api/hygiene/schemes",Map.of("name","模拟新阈值","threshold",90,"dimensions",List.of(Map.of("name","单项","weight",100))),201);
        post(f.admin,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",f.batch.path("schemeId").asLong()),200);
        assertThat(only(f)).isEqualTo(event);
        assertThat(get(f.admin,EVENTS+"/"+event.path("id").asLong())).isEqualTo(event);
    }
    private JsonNode action(Browser actor,JsonNode event,String action,String note,int status) throws Exception {
        return post(actor,EVENTS+"/"+event.path("id").asLong()+"/"+action,Map.of("version",event.path("version").asLong(),"note",note),status);
    }
    @Test void managersFollowVerifyAndCloseWithoutLosingHistoryAndNewLowStartsNextEvent() throws Exception {
        var f=fixture();post(f.manager,scorePath(f,f.batch),scores(f.batch,60,59),201);var initial=only(f);
        assertThat(action(f.admin,initial,"close","未经核实不能关闭",409).path("code").asText()).isEqualTo("RECTIFICATION_NOT_VERIFIED");
        var followed=action(f.manager,initial,"follow-ups","已联系寝室清洁地面",200);
        assertThat(action(f.admin,initial,"verify","旧页面不覆盖新跟进",409).path("code").asText()).isEqualTo("RECTIFICATION_CHANGED");
        var verified=action(f.admin,followed,"verify","现场核实地面已清洁",200);
        assertThat(verified.path("verifiedAt").isNull()).isFalse();
        var next=next(f,"2026-11-23T01:00:00Z");post(f.manager,scorePath(f,next),scores(next,0,0),201);
        var repeated=only(f);assertThat(repeated.path("id")).isEqualTo(initial.path("id"));
        assertThat(repeated.path("triggers").size()).isEqualTo(2);assertThat(repeated.path("verifiedAt").isNull()).isTrue();
        assertThat(action(f.admin,repeated,"close","新低分需要重新核实",409).path("code").asText()).isEqualTo("RECTIFICATION_NOT_VERIFIED");
        var latest=action(f.manager,repeated,"verify","再次实地核实整改完成",200);
        action(f.admin,latest,"close","   ",400);
        var closed=action(f.admin,latest,"close","核实通过并保留两次检查依据",200);
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");assertThat(closed.path("closedAt").isNull()).isFalse();
        assertThat(closed.path("logs").findValuesAsText("action")).containsExactly("TRIGGERED","FOLLOW_UP","VERIFIED","TRIGGERED","VERIFIED","CLOSED");
        assertThat(get(f.manager,EVENTS+"?status=OPEN").path("total").asInt()).isZero();
        action(f.manager,closed,"follow-ups","关闭后不能追加",409);
        var w3=next(f,"2026-11-30T01:00:00Z");post(f.manager,scorePath(f,w3),scores(w3,59,59),201);
        var open=get(f.manager,EVENTS+"?status=OPEN");assertThat(open.path("total").asInt()).isEqualTo(1);
        assertThat(open.path("items").get(0).path("id")).isNotEqualTo(initial.path("id"));
        assertThat(get(f.manager,EVENTS+"?status=CLOSED").path("items").get(0).path("id")).isEqualTo(initial.path("id"));
        assertThat(get(f.manager,EVENTS+"/"+initial.path("id").asLong()).path("logs")).isEqualTo(closed.path("logs"));
    }

    @Test void unfinishedRoomAndBuildingCannotBeDisabledAfterCheckoutButClosedHistoryRemains() throws Exception {
        var f=fixture();post(f.manager,scorePath(f,f.batch),scores(f.batch,0,0),201);var event=only(f);
        post(f.admin,"/api/housing/residencies/"+f.residency+"/checkout",Map.of("note","模拟退宿"),200);
        for(String path:List.of("/api/housing/rooms/"+f.room,"/api/housing/buildings/"+f.building))
            assertThat(post(f.admin,path+"/enabled",Map.of("version",0,"enabled",false),409).path("code").asText()).isEqualTo("UNFINISHED_RECTIFICATION");
        var verified=action(f.admin,event,"verify","现场确认寝室恢复卫生",200);
        var closed=action(f.manager,verified,"close","允许资料停用，历史保留",200);
        post(f.admin,"/api/housing/rooms/"+f.room+"/enabled",Map.of("version",0,"enabled",false),200);
        post(f.admin,"/api/housing/buildings/"+f.building+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(get(f.admin,EVENTS+"/"+event.path("id").asLong())).isEqualTo(closed);
    }

    private JsonNode state(Fixture f,JsonNode batch,String action) throws Exception {
        String path="/api/hygiene/batches/"+batch.path("id").asLong();
        return post(f.admin,path+"/state",Map.of("version",get(f.admin,path).path("version").asLong(),"action",action,"reason","模拟历史整改验证"),200);
    }
    @Test void concurrentHistoricalLowScoresReuseOneOpenEventAndRetryCannotDuplicateTriggers() throws Exception {
        var f=fixture();var w2=next(f,"2026-11-23T01:00:00Z");state(f,f.batch,"REOPEN");
        var other=login("dorm_manager");f.manager.csrf();other.csrf();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return post(f.manager,scorePath(f,f.batch),scores(f.batch,59,59),201);});
            var b=pool.submit(()->{gate.await();return post(other,scorePath(f,w2),scores(w2,0,0),201);});
            gate.countDown();a.get();b.get();
        } finally { pool.shutdownNow(); }
        var event=only(f);assertThat(event.path("triggers").size()).isEqualTo(2);
        assertThat(event.path("logs").findValuesAsText("action")).containsExactly("TRIGGERED","TRIGGERED");
        post(other,scorePath(f,f.batch),scores(f.batch,59,59),409);assertThat(only(f)).isEqualTo(event);
        // Verify the final database guard, in addition to the public concurrency behavior.
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("INSERT INTO hygiene_rectification(room_id,building_id,building_name,room_no,created_at,updated_at) SELECT room_id,building_id,building_name,room_no,created_at,updated_at FROM hygiene_rectification"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var actions=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            String path=EVENTS+"/"+event.path("id").asLong()+"/follow-ups";
            var body=Map.of("version",event.path("version").asLong(),"note","两个窗口仅允许一份写入");
            var a=actions.submit(()->{gate.await();return f.manager.postJson(path,body);});
            var b=actions.submit(()->{gate.await();return other.postJson(path,body);});
            gate.countDown();assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        } finally { actions.shutdownNow(); }
        assertThat(only(f).path("logs").size()).isEqualTo(3);
    }
    @Test void scoreEventLinkLogsAndDraftRollbackTogetherAndManagementAuditFailureKeepsState() throws Exception {
        var f=fixture();String roomPath=scorePath(f,f.batch).replace("/score","");
        var draft=post(f.manager,roomPath+"/draft",Map.of("version",0,"dimensions",List.of(Map.of("dimensionId",f.batch.path("dimensions").get(0).path("id").asLong(),"value","59"))),200);
        for(String table:List.of("hygiene_rectification_trigger","hygiene_rectification_log","operation_log")) {
            String predicate=table.equals("hygiene_rectification_trigger")?"total_score <> 59.99":table.equals("hygiene_rectification_log")?"action <> 'TRIGGERED'":"action <> 'SCORE_SUBMITTED'";
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_rectification_failure CHECK ("+predicate+")");
            try {
                var error=post(f.manager,scorePath(f,f.batch),scores(f.batch,60,59),500);
                assertThat(error.toString()).doesNotContain("SQL","Exception","test_rectification");
                assertThat(get(f.manager,roomPath).path("score").isNull()).isTrue();
                assertThat(get(f.manager,roomPath+"/draft").path("draft")).isEqualTo(draft);
                assertThat(get(f.manager,EVENTS).path("total").asInt()).isZero();
            } finally { jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_rectification_failure"); }
        }
        post(f.manager,scorePath(f,f.batch),scores(f.batch,60,59),201);var event=only(f);
        assertThat(get(f.manager,roomPath+"/draft").path("draft").isNull()).isTrue();
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_rectification_failure CHECK (action <> 'RECTIFICATION_VERIFIED')");
        try { action(f.admin,event,"verify","模拟核实写入失败",500);assertThat(only(f)).isEqualTo(event); }
        finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK test_rectification_failure"); }
        var verified=action(f.admin,event,"verify","模拟核实重试成功",200);
        jdbc.execute("ALTER TABLE hygiene_rectification_log ADD CONSTRAINT test_rectification_failure CHECK (action <> 'CLOSED')");
        try { action(f.admin,verified,"close","模拟关闭日志失败",500);assertThat(only(f)).isEqualTo(verified); }
        finally { jdbc.execute("ALTER TABLE hygiene_rectification_log DROP CHECK test_rectification_failure"); }
        assertThat(action(f.admin,verified,"close","模拟关闭重试成功",200).path("status").asText()).isEqualTo("CLOSED");
    }
    @Test void rolesCsrfRevocationValidationAndPaginationApplyToAllRectificationEndpoints() throws Exception {
        var f=fixture();post(f.manager,scorePath(f,f.batch),scores(f.batch,0,0),201);var event=only(f);
        String path=EVENTS+"/"+event.path("id").asLong();
        assertThat(new Browser().get(EVENTS).statusCode()).isEqualTo(401);
        for(String username:List.of("student","maintainer")) {
            var unauthorized=login(username);
            assertThat(unauthorized.get(EVENTS).statusCode()).isEqualTo(403);assertThat(unauthorized.get(path).statusCode()).isEqualTo(403);
            for(String operation:List.of("follow-ups","verify","close")) action(unauthorized,event,operation,"无权操作",403);
        }
        assertThat(f.manager.send(path+"/verify","POST","application/json",json.writeValueAsString(Map.of("version",0,"note","模拟CSRF缺失")),null).statusCode()).isEqualTo(403);
        for(String query:List.of("?status=bad","?page=0","?size=101","?size=0","?buildingId=-1","?q="+"a".repeat(65))) assertThat(f.admin.get(EVENTS+query).statusCode()).as(query).isEqualTo(400);
        assertThat(f.admin.get(EVENTS+"/999999999").statusCode()).isEqualTo(404);
        action(f.admin,event,"verify","",400);action(f.admin,event,"follow-ups","a".repeat(1001),400);
        post(f.admin,path+"/verify",Map.of("version",-1,"note","版本非法"),400);post(f.admin,path+"/close",Map.of("note","缺少版本"),400);
        assertThat(get(f.admin,EVENTS+"?q=101&buildingId="+f.building+"&size=1").path("total").asInt()).isEqualTo(1);
        assertThat(get(f.admin,EVENTS+"?page=2147483647&size=100").path("items").isEmpty()).isTrue();
        assertThat(get(f.admin,EVENTS+"?q=absent").path("total").asInt()).isZero();
        post(f.admin,"/api/identity/accounts/"+f.admin.account("dorm_manager").path("id").asLong()+"/reset-password",Map.of("version",0,"newPassword","AnotherPass2026!"),200);
        assertThat(f.manager.get(EVENTS).statusCode()).isEqualTo(401);action(f.manager,event,"verify","旧会话",401);
    }
    @Test void historicalEntryAndBatchSuspensionKeepTraceAndNeverAutomaticallyCloseEvents() throws Exception {
        var f=fixture();time.at("2026-11-23T01:00:00Z");state(f,f.batch,"REOPEN");
        var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,0,0),201);var event=only(f);
        assertThat(event.path("triggers").get(0).path("weekStart").asText()).isEqualTo("2026-11-16");
        assertThat(event.path("triggers").get(0).path("submittedAt").asText()).isEqualTo("2026-11-23T01:00:00Z");
        state(f,f.batch,"SUSPEND");var suspended=only(f);
        assertThat(suspended.path("status").asText()).isEqualTo("OPEN");assertThat(suspended.path("triggers").get(0).path("batchStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(get(f.manager,scorePath(f,f.batch).replace("/score","")).path("score")).isEqualTo(score);
        state(f,f.batch,"RESTORE");assertThat(only(f)).isEqualTo(event);
        var w2=next(f,"2026-11-23T01:00:00Z");post(f.manager,scorePath(f,w2),scores(w2,100,100),201);
        assertThat(only(f).path("status").asText()).isEqualTo("OPEN");assertThat(only(f).path("triggers").size()).isEqualTo(1);
    }

}
