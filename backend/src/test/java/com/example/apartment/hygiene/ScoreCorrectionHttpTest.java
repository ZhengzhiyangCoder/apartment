package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class ScoreCorrectionHttpTest extends HttpIntegrationTest {
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

    private String correctionPath(JsonNode score) { return "/api/hygiene/scores/"+score.path("id").asLong(); }
    private Map<String,Object> correction(JsonNode batch,long version,int first,int second,String reason) {
        var body=new HashMap<String,Object>(scores(batch,first,second));body.put("version",version);body.put("reason",reason);return body;
    }
    @Test void administratorCorrectsSameScoreUsingOriginalStandardAndKeepsFullHistoryInClosedBatch() throws Exception {
        var f=fixture();var original=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);
        post(f.admin,"/api/hygiene/batches/"+f.batch.path("id").asLong()+"/state",Map.of("version",0,"action","CLOSE","reason","模拟关闭"),200);
        var updated=post(f.admin,correctionPath(original)+"/corrections",correction(f.batch,0,90,70,"核对原始检查记录"),200);
        assertThat(updated.path("id")).isEqualTo(original.path("id"));assertThat(updated.path("batchId")).isEqualTo(original.path("batchId"));
        assertThat(updated.path("roomId")).isEqualTo(original.path("roomId"));assertThat(updated.path("submittedAt")).isEqualTo(original.path("submittedAt"));
        assertThat(updated.path("totalScore").decimalValue()).isEqualByComparingTo("89.80");assertThat(updated.path("version").asLong()).isEqualTo(1);
        assertThat(get(f.manager,scorePath(f,f.batch).replace("/score","")).path("score")).isEqualTo(updated);
        var history=get(f.manager,correctionPath(original)+"/corrections");assertThat(history.path("total").asInt()).isEqualTo(1);
        var entry=history.path("items").get(0);assertThat(entry.path("before")).isEqualTo(original);assertThat(entry.path("after")).isEqualTo(updated);
        assertThat(entry.path("reason").asText()).isEqualTo("核对原始检查记录");assertThat(entry.path("actorName").asText()).isEqualTo("模拟SYSTEM_ADMIN");
        assertThat(entry.path("createdAt").asText()).isEqualTo("2026-11-16T01:00:00Z");
        assertThat(get(f.admin,"/api/hygiene/batches/"+f.batch.path("id").asLong()).path("inspectedCount").asInt()).isEqualTo(1);
    }

    private JsonNode eventAction(Fixture f,JsonNode event,String action) throws Exception {
        return post(f.admin,EVENTS+"/"+event.path("id").asLong()+"/"+action,Map.of("version",event.path("version").asLong(),"note","模拟现场跟进核实"),200);
    }
    @Test void correctionsReuseTriggersKeepOriginalSnapshotAndFollowupsAndRequireFreshVerification() throws Exception {
        var f=fixture();var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);String path=correctionPath(score)+"/corrections";
        post(f.admin,path,correction(f.batch,0,59,59,"原误录为80，现核对为59"),200);
        var original=only(f);assertThat(original.path("triggers").get(0).path("totalScore").decimalValue()).isEqualByComparingTo("59.00");
        var followed=eventAction(f,original,"follow-ups");var verified=eventAction(f,followed,"verify");
        post(f.admin,path,correction(f.batch,1,50,50,"进一步核对为50"),200);
        var low=only(f);assertThat(low.path("id")).isEqualTo(original.path("id"));assertThat(low.path("triggers").size()).isEqualTo(1);
        assertThat(low.path("triggers").get(0).path("totalScore").decimalValue()).isEqualByComparingTo("59.00");
        assertThat(low.path("triggers").get(0).path("currentScore").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(low.path("verifiedAt").isNull()).isTrue();assertThat(low.path("logs").findValuesAsText("action")).contains("FOLLOW_UP","VERIFIED");
        post(f.admin,path,correction(f.batch,2,60,60,"原记录更正为达标"),200);
        var passed=only(f);assertThat(passed.path("status").asText()).isEqualTo("OPEN");
        assertThat(passed.path("triggers").get(0).path("corrected").asBoolean()).isTrue();
        assertThat(passed.path("triggers").get(0).path("currentScore").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(passed.path("logs").findValuesAsText("action")).contains("FOLLOW_UP","VERIFIED");
        post(f.admin,EVENTS+"/"+original.path("id").asLong()+"/close",Map.of("version",verified.path("version").asLong(),"note","旧核实不能覆盖更正"),409);
        var closed=eventAction(f,eventAction(f,passed,"verify"),"close");
        post(f.admin,path,correction(f.batch,3,0,0,"再次核对为零分"),200);
        var open=get(f.admin,EVENTS+"?status=OPEN");assertThat(open.path("total").asInt()).isEqualTo(1);
        assertThat(open.path("items").get(0).path("id")).isNotEqualTo(closed.path("id"));
        assertThat(get(f.admin,EVENTS+"/"+closed.path("id").asLong()).path("logs")).isEqualTo(closed.path("logs"));
        assertThat(get(f.manager,path).path("total").asInt()).isEqualTo(4);
    }

    @Test void onlyLiveAdministratorsCanCorrectWithCsrfAndValidOriginalDimensions() throws Exception {
        var f=fixture();var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);String path=correctionPath(score),changes=path+"/corrections";
        var body=correction(f.batch,0,90,70,"模拟授权检查");
        assertThat(new Browser().get(path).statusCode()).isEqualTo(401);
        for(String username:List.of("dorm_manager","student","maintainer")) {
            var b=login(username);post(b,changes,body,403);
            if(!username.equals("dorm_manager")) { assertThat(b.get(path).statusCode()).isEqualTo(403);assertThat(b.get(changes).statusCode()).isEqualTo(403); }
        }
        assertThat(f.admin.send(changes,"POST","application/json",json.writeValueAsString(body),null).statusCode()).isEqualTo(403);
        for(String reason:List.of("", "   ", "a".repeat(1001))) post(f.admin,changes,correction(f.batch,0,90,70,reason),400);
        for(long version:List.of(-1L,1L)) post(f.admin,changes,correction(f.batch,version,90,70,"版本校验"),version<0?400:409);
        post(f.admin,changes,scores(f.batch,90,70),400);
        for(int value:List.of(-1,101)) post(f.admin,changes,correction(f.batch,0,value,70,"分值校验"),400);
        long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        for(var dimensions:List.of(List.of(Map.of("dimensionId",dimension,"score",70)),List.of(Map.of("dimensionId",dimension,"score",70),Map.of("dimensionId",dimension,"score",80)),List.of(Map.of("dimensionId",99999999,"score",80)),List.of(Map.of("dimensionId",dimension,"score",70.5))))
            post(f.admin,changes,Map.of("version",0,"reason","维度校验","dimensions",dimensions),400);
        for(String query:List.of("?page=0","?size=101","?size=0")) assertThat(f.admin.get(changes+query).statusCode()).isEqualTo(400);
        assertThat(get(f.admin,changes+"?page=2147483647&size=100").path("items").isEmpty()).isTrue();
        assertThat(f.admin.get("/api/hygiene/scores/999999999").statusCode()).isEqualTo(404);
        post(f.admin,"/api/hygiene/scores/999999999/corrections",body,404);
        String oldCsrf=f.admin.csrf();var fresh=login("system_admin");var admin=f.admin.account("system_admin");
        post(fresh,"/api/identity/accounts/"+admin.path("id").asLong()+"/reset-password",Map.of("version",0,"newPassword","NewAdmin2026!"),200);
        assertThat(f.admin.send(changes,"POST","application/json",json.writeValueAsString(body),oldCsrf).statusCode()).isEqualTo(401);assertThat(f.admin.get(path).statusCode()).isEqualTo(401);
        assertThat(get(f.manager,path)).isEqualTo(score);
    }
    @Test void twoAdministratorsCannotOverwriteSameVersionAndHistoryPaginates() throws Exception {
        var f=fixture();post(f.admin,"/api/identity/accounts",Map.of("username","other_admin","displayName","模拟第二管理员","role","SYSTEM_ADMIN","password",PASSWORD),201);
        var other=login("other_admin");var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);String path=correctionPath(score);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return f.admin.postJson(path+"/corrections",correction(f.batch,0,90,90,"管理员甲"));});
            var b=pool.submit(()->{gate.await();return other.postJson(path+"/corrections",correction(f.batch,0,70,70,"管理员乙"));});
            gate.countDown();assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        var current=get(f.admin,path);assertThat(current.path("version").asInt()).isEqualTo(1);
        assertThat(get(f.admin,path+"/corrections").path("total").asInt()).isEqualTo(1);
        post(other,path+"/corrections",correction(f.batch,1,85,85,"核对后再次更正"),200);
        var history=get(f.admin,path+"/corrections?size=1&page=2");assertThat(history.path("total").asInt()).isEqualTo(2);
        assertThat(history.path("items").get(0).path("after")).isEqualTo(current);
        assertThat(history.path("items").get(0).path("before")).isEqualTo(score);
    }
    @Test void immutableOriginalSchemeAndThresholdApplyEvenAfterActiveSchemeChanges() throws Exception {
        var f=fixture();var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);
        var scheme=post(f.admin,"/api/hygiene/schemes",Map.of("name","模拟新版标准","threshold",90,"dimensions",List.of(Map.of("name","新维度","weight",100))),201);
        post(f.admin,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",f.batch.path("schemeId").asLong()),200);
        var invalid=Map.of("version",0,"reason","不能使用新方案","dimensions",List.of(Map.of("dimensionId",scheme.path("dimensions").get(0).path("id").asLong(),"score",80)));
        post(f.admin,correctionPath(score)+"/corrections",invalid,400);
        var body=new HashMap<String,Object>(correction(f.batch,0,60,60,"仍按原阈值60达标"));body.put("roomId",999999);body.put("batchId",999999);
        var updated=post(f.admin,correctionPath(score)+"/corrections",body,200);
        assertThat(updated.path("threshold").decimalValue()).isEqualByComparingTo("60.00");assertThat(updated.path("roomId")).isEqualTo(score.path("roomId"));assertThat(updated.path("batchId")).isEqualTo(score.path("batchId"));
        assertThat(get(f.admin,EVENTS).path("total").asInt()).isZero();
    }
    @Test void scoreDimensionsCorrectionRectificationAndAuditRollbackAsOneTransaction() throws Exception {
        var f=fixture();var score=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);String path=correctionPath(score);
        var faults=Map.of("hygiene_score_dimension","score <> 50","hygiene_score_correction","version <> 1","hygiene_rectification_trigger","total_score <> 50","hygiene_rectification_log","action <> 'TRIGGERED'","operation_log","action <> 'SCORE_CORRECTED'");
        for(var fault:faults.entrySet()) {
            jdbc.execute("ALTER TABLE "+fault.getKey()+" ADD CONSTRAINT test_correction_failure CHECK ("+fault.getValue()+")");
            try {
                var error=post(f.admin,path+"/corrections",correction(f.batch,0,50,50,"模拟事务故障"),500);
                assertThat(error.toString()).doesNotContain("SQL","Exception","test_correction");assertThat(get(f.manager,path)).isEqualTo(score);
                assertThat(get(f.manager,path+"/corrections").path("total").asInt()).isZero();assertThat(get(f.admin,EVENTS).path("total").asInt()).isZero();
            } finally { jdbc.execute("ALTER TABLE "+fault.getKey()+" DROP CHECK test_correction_failure"); }
        }
        var low=post(f.admin,path+"/corrections",correction(f.batch,0,50,50,"故障解除后重试"),200);var event=eventAction(f,only(f),"follow-ups");
        jdbc.execute("ALTER TABLE hygiene_rectification_log ADD CONSTRAINT test_correction_failure CHECK (action <> 'CORRECTED')");
        try { post(f.admin,path+"/corrections",correction(f.batch,1,90,90,"模拟达标联动失败"),500);assertThat(get(f.manager,path)).isEqualTo(low);assertThat(only(f)).isEqualTo(event);assertThat(get(f.admin,path+"/corrections").path("total").asInt()).isEqualTo(1); }
        finally { jdbc.execute("ALTER TABLE hygiene_rectification_log DROP CHECK test_correction_failure"); }
    }
    private void state(Fixture f,JsonNode batch,String action) throws Exception {
        String path="/api/hygiene/batches/"+batch.path("id").asLong();
        post(f.admin,path+"/state",Map.of("version",get(f.admin,path).path("version").asLong(),"action",action,"reason","模拟统计校验"),200);
    }
    @Test void correctionsUpdateWeeklyMonthlyRanksTrendsDimensionsAndExcludeSuspendedSamples() throws Exception {
        var f=fixture();var student=login("student");var first=post(f.manager,scorePath(f,f.batch),scores(f.batch,80,80),201);
        post(f.admin,"/api/identity/accounts",Map.of("username","peer_student","displayName","模拟同楼学生","role","STUDENT","password",PASSWORD),201);
        long peer=post(f.admin,"/api/housing/rooms",Map.of("buildingId",f.building,"roomNo","102","floor",1),201).path("id").asLong();
        long bed=post(f.admin,"/api/housing/beds",Map.of("roomId",peer,"bedNo","01"),201).path("id").asLong();
        long profile=post(f.admin,"/api/housing/students",Map.of("userId",f.admin.account("peer_student").path("id").asLong(),"studentNo","peer_student","fullName","模拟同楼学生","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        post(f.admin,"/api/housing/residencies",Map.of("studentId",profile,"bedId",bed),201);
        post(f.admin,"/api/hygiene/batches/"+f.batch.path("id").asLong()+"/rooms/"+peer+"/add",Map.of("version",0,"reason","模拟同楼对比"),200);
        post(f.manager,"/api/hygiene/batches/"+f.batch.path("id").asLong()+"/rooms/"+peer+"/score",scores(f.batch,90,90),201);
        var w2=next(f,"2026-11-23T01:00:00Z");post(f.manager,scorePath(f,w2),scores(w2,100,100),201);
        assertThat(get(student,"/api/hygiene/student/results?period=MONTH&date=2026-11-01").path("summary").path("rank").asInt()).isEqualTo(1);
        post(f.admin,correctionPath(first)+"/corrections",correction(f.batch,0,60,60,"原周80更正60"),200);
        String query="?period=MONTH&date=2026-11-01";
        var monthly=get(student,"/api/hygiene/student/results"+query);assertThat(monthly.path("summary").path("score").decimalValue()).isEqualByComparingTo("80.00");assertThat(monthly.path("summary").path("samples").asInt()).isEqualTo(2);
        assertThat(monthly.path("trend").get(11).path("score").decimalValue()).isEqualByComparingTo("80.00");
        var weekly=get(student,"/api/hygiene/student/results?date=2026-11-16");assertThat(weekly.path("summary").path("score").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(weekly.path("weeks").get(0).path("dimensions").get(0).path("score").asInt()).isEqualTo(60);
        var stats=get(f.manager,"/api/hygiene/statistics"+query+"&roomId="+f.room);
        assertThat(monthly.path("summary").path("rank").asInt()).isEqualTo(2);
        var building=get(f.manager,"/api/hygiene/statistics"+query);
        assertThat(building.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(building.path("trend").get(0).path("points").get(11).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(building.path("dimensions").get(0).path("dimensions").get(0).path("score").decimalValue()).isEqualByComparingTo("83.33");
        assertThat(stats.path("rankings").path("items").get(0).path("rank").asInt()).isEqualTo(2);
        assertThat(stats.path("rankings").path("items").get(0).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(stats.path("trend").get(0).path("points").get(11).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(stats.path("dimensions").get(0).path("dimensions").get(0).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(stats.path("weeks").get(2).path("totalScore").decimalValue()).isEqualByComparingTo("60.00");
        state(f,f.batch,"SUSPEND");post(f.admin,correctionPath(first)+"/corrections",correction(f.batch,1,40,40,"停检仍留痕更正"),200);
        assertThat(get(student,"/api/hygiene/student/results"+query).path("summary").path("score").decimalValue()).isEqualByComparingTo("100.00");
        var excluded=get(f.admin,"/api/hygiene/statistics"+query);assertThat(excluded.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("100.00");assertThat(excluded.path("summary").path("samples").asInt()).isEqualTo(1);
        state(f,f.batch,"RESTORE");
        assertThat(get(student,"/api/hygiene/student/results"+query).path("summary").path("score").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(get(f.admin,"/api/hygiene/statistics"+query).path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(get(f.admin,correctionPath(first)+"/corrections").path("items").get(1).path("before").path("totalScore").decimalValue()).isEqualByComparingTo("80.00");
    }
}
