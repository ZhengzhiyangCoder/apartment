package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class BatchGovernanceHttpTest extends HttpIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired BatchHttpTest.MutableClock time;
    @org.junit.jupiter.api.BeforeEach void resetTime() { time.at("2026-11-16T01:00:00Z"); }
    private static final String BASE="/api/hygiene/batches";
    private Browser login(String name) throws Exception { var b=new Browser(); assertThat(b.login(name,PASSWORD).statusCode()).isEqualTo(200); return b; }
    private JsonNode get(Browser b,String path) throws Exception { var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body()); }
    private JsonNode post(Browser b,String path,Object body,int status) throws Exception {var r=b.postJson(path,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body());}
    private record Fixture(Browser admin,Browser manager,JsonNode batch,long room,long building) {
        String path(){return BASE+"/"+batch.path("id").asLong();}
        String roomPath(){return path()+"/rooms/"+room;}
    }
    private long occupiedRoom(Browser admin,long building,String number,String username) throws Exception {
        long room=post(admin,"/api/housing/rooms",Map.of("buildingId",building,"roomNo",number,"floor",1),201).path("id").asLong();
        long bed=post(admin,"/api/housing/beds",Map.of("roomId",room,"bedNo","01"),201).path("id").asLong();
        if(!username.equals("student")) post(admin,"/api/identity/accounts",Map.of("username",username,"displayName","模拟学生","role","STUDENT","password",PASSWORD),201);
        long student=post(admin,"/api/housing/students",Map.of("userId",admin.account(username).path("id").asLong(),"studentNo",username,"fullName","模拟学生","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        post(admin,"/api/housing/residencies",Map.of("studentId",student,"bedId",bed),201);return room;
    }
    private Fixture fixture() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long scheme=post(admin,"/api/hygiene/schemes",Map.of("name","模拟治理标准","threshold",65,"dimensions",List.of(Map.of("name","整洁","weight",100))),201).path("id").asLong();
        post(admin,"/api/hygiene/schemes/"+scheme+"/activate",Map.of("version",0,"activeId",0),200);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟治理楼栋"),201).path("id").asLong();
        long room=occupiedRoom(admin,building,"101","student");
        return new Fixture(admin,manager,post(manager,BASE+"/current/prepare",Map.of(),200),room,building);
    }
    private Object scores(Fixture f){return Map.of("dimensions",List.of(Map.of("dimensionId",f.batch.path("dimensions").get(0).path("id").asLong(),"score",80)));}
    private Object draft(Fixture f,long version){return Map.of("version",version,"dimensions",List.of(Map.of("dimensionId",f.batch.path("dimensions").get(0).path("id").asLong(),"value","80")));}
    private JsonNode change(Fixture f,String action,long version) throws Exception {return post(f.admin,f.path()+"/state",Map.of("version",version,"action",action,"reason","模拟治理原因"),200);}

    @Test
    void closingSuspendingAndRestoringKeepDraftsAndScoresWhileControllingSubmission() throws Exception {
        var f=fixture();var personal=post(f.manager,f.roomPath()+"/draft",draft(f,0),200);
        var closed=change(f,"CLOSE",0);assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.path("scoringAllowed").asBoolean()).isFalse();
        post(f.manager,f.roomPath()+"/score",scores(f),409);post(f.manager,f.roomPath()+"/draft",draft(f,1),409);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").path("dimensions")).isEqualTo(personal.path("dimensions"));
        var suspended=change(f,"SUSPEND",1);assertThat(suspended.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(change(f,"RESTORE",2).path("status").asText()).isEqualTo("CLOSED");
        assertThat(change(f,"REOPEN",3).path("scoringAllowed").asBoolean()).isTrue();
        change(f,"SUSPEND",4);post(f.manager,f.roomPath()+"/score",scores(f),409);
        assertThat(change(f,"RESTORE",5).path("status").asText()).isEqualTo("OPEN");
        var saved=post(f.manager,f.roomPath()+"/score",scores(f),201);
        assertThat(get(f.manager,f.path()).path("status").asText()).isEqualTo("OPEN");
        change(f,"SUSPEND",6);assertThat(get(f.manager,f.roomPath()).path("score")).isEqualTo(saved);
        change(f,"RESTORE",7);post(f.manager,f.roomPath()+"/score",scores(f),409);
        assertThat(get(f.manager,f.path()).path("completionRate").asInt()).isEqualTo(100);
    }
    @Test
    void listAdjustmentsTrackReasonsPreserveRemovedDraftsAndRejectScoredOrIneligibleRooms() throws Exception {
        var f=fixture();var personal=post(f.manager,f.roomPath()+"/draft",draft(f,0),200);
        long added=occupiedRoom(f.admin,f.building,"102","late_student");
        assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isEqualTo(1);
        var next=post(f.admin,f.path()+"/rooms/"+added+"/add",Map.of("version",0,"reason","补入遗漏寝室"),200);
        assertThat(next.path("expectedCount").asInt()).isEqualTo(2);
        post(f.admin,f.roomPath()+"/remove",Map.of("version",1,"reason","移出错误名单"),200);
        assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isEqualTo(1);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(personal);
        assertThat(post(f.manager,f.roomPath()+"/score",scores(f),404).path("code").asText()).isEqualTo("NOT_EXPECTED_ROOM");
        post(f.manager,f.roomPath()+"/draft",draft(f,1),404);
        var candidate=get(f.admin,f.path()+"/eligible-rooms?q=101&size=1");assertThat(candidate.path("total").asInt()).isEqualTo(1);
        assertThat(candidate.path("items").get(0).path("roomId").asLong()).isEqualTo(f.room);
        var history=get(f.admin,f.path()+"/changes?size=1");assertThat(history.path("total").asInt()).isEqualTo(2);
        var removal=history.path("items").get(0);assertThat(removal.path("action").asText()).isEqualTo("BATCH_ROOM_REMOVED");
        assertThat(removal.path("before").path("room").path("roomId").asLong()).isEqualTo(f.room);
        assertThat(removal.path("after").path("reason").asText()).isEqualTo("移出错误名单");
        assertThat(removal.path("actorId").asLong()).isEqualTo(f.admin.account("system_admin").path("id").asLong());
        assertThat(removal.path("createdAt").asText()).isNotBlank();
        assertThat(get(f.manager,f.path()+"/changes?size=1&page=2").path("items").get(0).path("after").path("room").path("roomId").asLong()).isEqualTo(added);
        post(f.manager,f.path()+"/rooms/"+added+"/score",scores(f),201);
        post(f.admin,f.path()+"/rooms/"+added+"/remove",Map.of("version",2,"reason","不能移出已有成绩"),409);
        long residency=get(f.admin,"/api/housing/residencies?current=true").path("items").get(0).path("id").asLong();
        post(f.admin,"/api/housing/residencies/"+residency+"/checkout",Map.of("note","模拟退宿"),200);
        assertThat(get(f.manager,f.path()).path("completionRate").asInt()).isEqualTo(100);
        post(f.admin,f.roomPath()+"/add",Map.of("version",2,"reason","重新纳入"),200);
        post(f.admin,f.roomPath()+"/remove",Map.of("version",3,"reason","再次移出"),200);
        long originalResidency=get(f.admin,"/api/housing/residencies?current=true").path("items").get(0).path("id").asLong();
        post(f.admin,"/api/housing/residencies/"+originalResidency+"/checkout",Map.of("note","模拟退宿"),200);
        assertThat(post(f.admin,f.roomPath()+"/add",Map.of("version",4,"reason","空寝不符合补入条件"),409).path("code").asText()).isEqualTo("ROOM_NOT_ELIGIBLE");
        assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isEqualTo(1);
    }
    @Test
    void explicitlyReopenedHistorySurvivesRepeatedRolloverAndKeepsOriginalAttribution() throws Exception {
        var f=fixture();post(f.manager,f.roomPath()+"/draft",draft(f,0),200);
        time.at("2026-11-23T01:00:00Z");
        post(f.manager,f.roomPath()+"/score",scores(f),409);
        post(f.manager,BASE+"/current/prepare",Map.of(),200);
        var expired=get(f.manager,f.path());assertThat(expired.path("status").asText()).isEqualTo("CLOSED");
        assertThat(expired.path("version").asLong()).isEqualTo(1);
        var reopened=change(f,"REOPEN",1);assertThat(reopened.path("historicalReopened").asBoolean()).isTrue();
        assertThat(reopened.path("scoringAllowed").asBoolean()).isTrue();
        for(int n=0;n<2;n++) post(f.manager,BASE+"/current/prepare",Map.of(),200);
        assertThat(get(f.manager,f.path())).isEqualTo(reopened);
        change(f,"SUSPEND",2);post(f.manager,f.roomPath()+"/score",scores(f),409);
        assertThat(change(f,"RESTORE",3).path("scoringAllowed").asBoolean()).isTrue();
        long scheme=post(f.admin,"/api/hygiene/schemes",Map.of("name","模拟后续标准","threshold",90,"dimensions",List.of(Map.of("name","其他维度","weight",100))),201).path("id").asLong();
        post(f.admin,"/api/hygiene/schemes/"+scheme+"/activate",Map.of("version",0,"activeId",f.batch.path("schemeId").asLong()),200);
        time.at("2026-11-30T01:00:00Z");post(f.manager,BASE+"/current/prepare",Map.of(),200);
        post(f.manager,f.roomPath()+"/draft",draft(f,1),200);
        var saved=post(f.manager,f.roomPath()+"/score",scores(f),201);
        assertThat(saved.path("submittedAt").asText()).isEqualTo("2026-11-30T01:00:00Z");
        assertThat(saved.path("threshold").asInt()).isEqualTo(65);
        assertThat(saved.path("dimensions").get(0).path("name").asText()).isEqualTo("整洁");
        assertThat(get(f.manager,f.path()).path("weekStart").asText()).isEqualTo("2026-11-16");
        assertThat(get(f.manager,f.path()).path("schemeId")).isEqualTo(f.batch.path("schemeId"));
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
        post(f.manager,f.roomPath()+"/score",scores(f),409);
        var history=get(f.manager,BASE+"?page=3&size=1");assertThat(history.path("total").asInt()).isEqualTo(3);
        assertThat(history.path("items").get(0).path("id")).isEqualTo(f.batch.path("id"));
        var changes=get(f.admin,f.path()+"/changes");assertThat(changes.path("total").asInt()).isEqualTo(4);
        assertThat(changes.path("items").get(3).path("action").asText()).isEqualTo("BATCH_EXPIRED");
        change(f,"CLOSE",4);assertThat(get(f.manager,f.path()).path("historicalReopened").asBoolean()).isFalse();
    }
    @Test
    void suspendedOrdinaryBatchRestoresClosedAfterRolloverAndEmptyRosterHasNoPercentage() throws Exception {
        var f=fixture();change(f,"SUSPEND",0);
        time.at("2026-11-23T01:00:00Z");post(f.manager,BASE+"/current/prepare",Map.of(),200);
        var restored=change(f,"RESTORE",1);assertThat(restored.path("status").asText()).isEqualTo("CLOSED");
        assertThat(restored.path("scoringAllowed").asBoolean()).isFalse();
        post(f.admin,f.roomPath()+"/remove",Map.of("version",2,"reason","清理错误应检"),200);
        var empty=get(f.manager,f.path());assertThat(empty.path("expectedCount").asInt()).isZero();assertThat(empty.path("completionRate").isNull()).isTrue();
        assertThat(get(f.manager,f.path()+"/rooms").path("items").isEmpty()).isTrue();
        // A not-yet-materialized old OPEN batch can also be explicitly authorized.
        var current=get(f.manager,BASE+"/current").path("batch");time.at("2026-11-30T01:00:00Z");
        var reopened=post(f.admin,BASE+"/"+current.path("id").asLong()+"/state",Map.of("version",0,"action","REOPEN","reason","历史补录"),200);
        assertThat(reopened.path("historicalReopened").asBoolean()).isTrue();
        assertThat(reopened.path("scoringAllowed").asBoolean()).isTrue();
    }
    @Test
    void governanceRejectsUnauthorizedStaleInvalidAndRevokedRequestsWithoutChanges() throws Exception {
        var f=fixture();var state=Map.of("version",0,"action","CLOSE","reason","模拟关闭");var roster=Map.of("version",0,"reason","模拟移出");
        assertThat(new Browser().get(BASE).statusCode()).isEqualTo(401);
        for(var actor:List.of(f.manager,login("student"),login("maintainer"))) {
            post(actor,f.path()+"/state",state,403);post(actor,f.roomPath()+"/remove",roster,403);post(actor,f.roomPath()+"/add",roster,403);
            assertThat(actor.get(f.path()+"/eligible-rooms").statusCode()).isEqualTo(403);
        }
        for(var actor:List.of(login("student"),login("maintainer"))) {
            assertThat(actor.get(BASE).statusCode()).isEqualTo(403);assertThat(actor.get(f.path()+"/changes").statusCode()).isEqualTo(403);
        }
        assertThat(f.admin.send(f.path()+"/state","POST","application/json",json.writeValueAsString(state),null).statusCode()).isEqualTo(403);
        for(Object invalid:List.of(Map.of(),Map.of("version",-1,"action","CLOSE","reason","原因"),Map.of("version",0,"action","BAD","reason","原因"),
                Map.of("version",0,"action","CLOSE","reason"," "),Map.of("version",0,"action","CLOSE","reason","长".repeat(501)))) post(f.admin,f.path()+"/state",invalid,400);
        post(f.admin,f.roomPath()+"/remove",Map.of("version",0,"reason",""),400);
        for(String path:List.of(BASE,f.path()+"/changes",f.path()+"/eligible-rooms")) {
            for(String query:List.of("page=0","size=101","size=0")) assertThat(f.admin.get(path+"?"+query).statusCode()).isEqualTo(400);
            assertThat(get(f.admin,path+"?page=100").path("items").isEmpty()).isTrue();
        }
        post(f.admin,f.path()+"/state",Map.of("version",1,"action","CLOSE","reason","过期版本"),409);
        post(f.admin,f.path()+"/state",Map.of("version",0,"action","RESTORE","reason","错误状态"),409);
        assertThat(get(f.manager,f.path())).isEqualTo(f.batch);
        assertThat(get(f.manager,f.path()+"/changes").path("total").asInt()).isZero();
        change(f,"CLOSE",0);
        assertThat(post(f.admin,f.roomPath()+"/remove",roster,409).path("code").asText()).isEqualTo("BATCH_CHANGED");
        post(f.admin,f.path()+"/state",Map.of("version",1,"action","CLOSE","reason","重复关闭"),409);
        long secondId=post(f.admin,"/api/identity/accounts",Map.of("username","second_admin","displayName","模拟系统管理员","role","SYSTEM_ADMIN","password",PASSWORD),201).path("id").asLong();
        var second=login("second_admin");var token=second.csrf();post(f.admin,"/api/identity/accounts/"+secondId+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(second.send(f.path()+"/state","POST","application/json",json.writeValueAsString(Map.of("version",1,"action","REOPEN","reason","旧会话")),token).statusCode()).isEqualTo(401);
    }
    @Test
    void auditFailureRollsBackRosterStatusAndRolloverWithDraftsUntouched() throws Exception {
        var f=fixture();var personal=post(f.manager,f.roomPath()+"/draft",draft(f,0),200);
        long extra=occupiedRoom(f.admin,f.building,"102","late_student");
        String add=f.path()+"/rooms/"+extra+"/add";
        for(String action:List.of("BATCH_ROOM_ADDED","BATCH_ROOM_REMOVED","BATCH_CLOSED","BATCH_EXPIRED")) {
            var before=get(f.manager,f.path());var beforeLog=get(f.manager,f.path()+"/changes");
            jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_reject_governance CHECK (action <> '"+action+"')");
            try {
                if(action.equals("BATCH_ROOM_ADDED")) post(f.admin,add,Map.of("version",0,"reason","模拟新增故障"),500);
                else if(action.equals("BATCH_ROOM_REMOVED")) post(f.admin,f.roomPath()+"/remove",Map.of("version",0,"reason","模拟移除故障"),500);
                else if(action.equals("BATCH_CLOSED")) post(f.admin,f.path()+"/state",Map.of("version",0,"action","CLOSE","reason","模拟关闭故障"),500);
                else {time.at("2026-11-23T01:00:00Z");post(f.manager,BASE+"/current/prepare",Map.of(),500);assertThat(get(f.manager,BASE+"/current").path("batch").isNull()).isTrue();time.at("2026-11-16T01:00:00Z");}
                assertThat(get(f.manager,f.path())).isEqualTo(before);assertThat(get(f.manager,f.path()+"/changes")).isEqualTo(beforeLog);
                assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(personal);
                assertThat(get(f.manager,f.path()+"/rooms").path("total").asInt()).isEqualTo(1);
            } finally {jdbc.execute("ALTER TABLE operation_log DROP CHECK test_reject_governance");}
        }
        post(f.admin,add,Map.of("version",0,"reason","恢复后新增"),200);assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isEqualTo(2);
    }
    @Test
    void concurrentRosterOrStateChangeAndSubmissionCannotBypassTransactionalValidation() throws Exception {
        var f=fixture();f.admin.csrf();f.manager.csrf();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var remove=pool.submit(()->{gate.await();return f.admin.postJson(f.roomPath()+"/remove",Map.of("version",0,"reason","并发移出"));});
            var submit=pool.submit(()->{gate.await();return f.manager.postJson(f.roomPath()+"/score",scores(f));});
            gate.countDown();var r=remove.get();var s=submit.get();
            if(r.statusCode()==200) {
                assertThat(s.statusCode()).isEqualTo(404);assertThat(get(f.manager,f.path()).path("inspectedCount").asInt()).isZero();
                assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isZero();
            } else {assertThat(r.statusCode()).isEqualTo(409);assertThat(s.statusCode()).isEqualTo(201);assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isFalse();}
            long extra=occupiedRoom(f.admin,f.building,"102","race_student");long version=get(f.admin,f.path()).path("version").asLong();
            var added=post(f.admin,f.path()+"/rooms/"+extra+"/add",Map.of("version",version,"reason","并发状态样本"),200);
            var start=new java.util.concurrent.CountDownLatch(1);
            var close=pool.submit(()->{start.await();return f.admin.postJson(f.path()+"/state",Map.of("version",added.path("version").asLong(),"action","CLOSE","reason","并发关闭"));});
            var score=pool.submit(()->{start.await();return f.manager.postJson(f.path()+"/rooms/"+extra+"/score",scores(f));});
            start.countDown();assertThat(close.get().statusCode()).isEqualTo(200);var response=score.get();assertThat(response.statusCode()).isIn(201,409);
            assertThat(get(f.manager,f.path()).path("status").asText()).isEqualTo("CLOSED");
            assertThat(get(f.manager,f.path()+"/rooms/"+extra).path("score").isNull()).isEqualTo(response.statusCode()==409);
            post(f.manager,f.path()+"/rooms/"+extra+"/score",scores(f),409);
            long currentVersion=get(f.admin,f.path()).path("version").asLong();var second=login("system_admin");var both=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{both.await();return f.admin.postJson(f.path()+"/state",Map.of("version",currentVersion,"action","SUSPEND","reason","并发停检"));});
            var b=pool.submit(()->{both.await();return second.postJson(f.path()+"/state",Map.of("version",currentVersion,"action","REOPEN","reason","并发重开"));});
            both.countDown();assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
    }
    @Test
    void housingReferenceProtectionMatchesCurrentAndAuthorizedHistoricalEntry() throws Exception {
        var f=fixture();long residency=get(f.admin,"/api/housing/residencies?current=true").path("items").get(0).path("id").asLong();
        post(f.admin,"/api/housing/residencies/"+residency+"/checkout",Map.of("note","模拟退宿"),200);
        String room="/api/housing/rooms/"+f.room+"/enabled";
        post(f.admin,room,Map.of("version",0,"enabled",false),409);
        time.at("2026-11-23T01:00:00Z");
        post(f.admin,room,Map.of("version",0,"enabled",false),200); // Expired ordinary entry no longer blocks deactivation.
        post(f.admin,room,Map.of("version",1,"enabled",true),200);
        change(f,"REOPEN",0);post(f.admin,room,Map.of("version",2,"enabled",false),409);
        change(f,"SUSPEND",1);post(f.admin,room,Map.of("version",2,"enabled",false),200);
        change(f,"RESTORE",2);
        post(f.admin,"/api/housing/buildings/"+f.building+"/enabled",Map.of("version",0,"enabled",false),409);
        change(f,"CLOSE",3);
        post(f.admin,"/api/housing/buildings/"+f.building+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(get(f.manager,f.path()).path("expectedCount").asInt()).isEqualTo(1);
    }
}
