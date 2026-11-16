package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class BatchHttpTest extends HttpIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TimeControl {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends java.time.Clock {
        volatile java.time.Instant now=java.time.Instant.parse("2026-11-16T01:00:00Z");
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return java.time.Clock.fixed(now,zone); }
        public java.time.Instant instant() { return now; }
        void at(String time) { now=java.time.Instant.parse(time); }
    }
    @org.springframework.beans.factory.annotation.Autowired MutableClock time;
    @org.junit.jupiter.api.BeforeEach void resetTime() { time.at("2026-11-16T01:00:00Z"); }
    private static final String BASE = "/api/hygiene/batches";
    private Browser login(String name) throws Exception { var b = new Browser(); assertThat(b.login(name, PASSWORD).statusCode()).isEqualTo(200); return b; }
    private JsonNode get(Browser b, String path) throws Exception { var r=b.get(path); assertThat(r.statusCode()).as(r.body()).isEqualTo(200); return json.readTree(r.body()); }
    private JsonNode post(Browser b, String path, Object body, int status) throws Exception { var r=b.postJson(path,body); assertThat(r.statusCode()).as(r.body()).isEqualTo(status); return json.readTree(r.body()); }
    private JsonNode scheme(Browser admin) throws Exception {
        var s=post(admin,"/api/hygiene/schemes",Map.of("name","模拟批次标准","dimensions",List.of(Map.of("name","地面","weight",40),Map.of("name","摆放","weight",30),Map.of("name","整洁","weight",30))),201);
        return post(admin,"/api/hygiene/schemes/"+s.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
    }
    private JsonNode room(Browser admin, long building, String number, String student) throws Exception {
        var room=post(admin,"/api/housing/rooms",Map.of("buildingId",building,"roomNo",number,"floor",1),201);
        var bed=post(admin,"/api/housing/beds",Map.of("roomId",room.path("id").asLong(),"bedNo","01"),201);
        if(student != null) {
            if(!student.equals("student")) post(admin,"/api/identity/accounts",Map.of("username",student,"displayName","模拟学生","role","STUDENT","password",PASSWORD),201);
            var profile=post(admin,"/api/housing/students",Map.of("userId",admin.account(student).path("id").asLong(),"studentNo",student,"fullName","模拟学生","college","模拟学院","major","模拟专业","className","模拟班级"),201);
            post(admin,"/api/housing/residencies",Map.of("studentId",profile.path("id").asLong(),"bedId",bed.path("id").asLong()),201);
        }
        return room;
    }
    @Test
    void readOnlyCurrentDoesNotCreateAndExplicitPreparationSnapshotsOccupiedRoomsAndScheme() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        assertThat(get(manager,BASE+"/current").path("batch").isNull()).isTrue();
        assertThat(post(manager,BASE+"/current/prepare",Map.of(),409).path("code").asText()).isEqualTo("NO_ACTIVE_SCHEME");
        var standard=scheme(admin);
        long a=post(admin,"/api/housing/buildings",Map.of("name","模拟A栋"),201).path("id").asLong();
        long b=post(admin,"/api/housing/buildings",Map.of("name","模拟B栋"),201).path("id").asLong();
        room(admin,a,"101","student");room(admin,a,"102","student_two");room(admin,b,"201","student_three");room(admin,b,"202",null);
        var batch=post(manager,BASE+"/current/prepare",Map.of(),200);long id=batch.path("id").asLong();
        assertThat(batch.path("schemeId")).isEqualTo(standard.path("id"));
        assertThat(batch.path("expectedCount").asInt()).isEqualTo(3);assertThat(batch.path("inspectedCount").asInt()).isZero();
        assertThat(batch.path("dimensions")).isEqualTo(standard.path("dimensions"));
        assertThat(post(admin,BASE+"/current/prepare",Map.of(),200)).isEqualTo(batch);
        var rows=get(manager,BASE+"/"+id+"/rooms");assertThat(rows.path("total").asInt()).isEqualTo(3);
        assertThat(rows.path("items").get(0).path("scoreId").isNull()).isTrue();assertThat(rows.path("items").get(0).path("totalScore").isNull()).isTrue();
        assertThat(get(admin,BASE+"/current").path("batch")).isEqualTo(batch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='BATCH_PREPARED'",Long.class)).isEqualTo(1);
    }
    private Object scores(JsonNode batch, int... values) {
        var dimensions=new ArrayList<Map<String,Object>>();
        for(int i=0;i<values.length;i++) dimensions.add(Map.of("dimensionId",batch.path("dimensions").get(i).path("id").asLong(),"score",values[i]));
        return Map.of("dimensions",dimensions,"totalScore",1,"submittedBy",1);
    }
    @Test
    void managerSubmitsExactWeightedScoresAndProgressKeepsUninspectedDistinctFromZero() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟评分楼栋"),201).path("id").asLong();
        var first=room(admin,building,"101","student");var second=room(admin,building,"102","student_two");room(admin,building,"103","student_three");
        var batch=post(admin,BASE+"/current/prepare",Map.of(),200);long id=batch.path("id").asLong();
        String path=BASE+"/"+id+"/rooms/"+first.path("id").asLong();
        var score=post(manager,path+"/score",scores(batch,80,90,70),201);
        assertThat(score.path("totalScore").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(score.path("threshold").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(score.path("dimensions").get(0).path("name").asText()).isEqualTo("地面");
        assertThat(score.path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(score.path("submittedBy")).isEqualTo(admin.account("dorm_manager").path("id"));
        assertThat(get(admin,path).path("score")).isEqualTo(score);
        assertThat(post(manager,path+"/score",scores(batch,80,90,70),409).path("code").asText()).isEqualTo("ALREADY_SCORED");
        post(manager,BASE+"/"+id+"/rooms/"+second.path("id").asLong()+"/score",scores(batch,0,0,0),201);
        var progress=get(manager,BASE+"/"+id);assertThat(progress.path("inspectedCount").asInt()).isEqualTo(2);
        assertThat(progress.path("completionRate").decimalValue()).isEqualByComparingTo("66.67");
        var pending=get(manager,BASE+"/"+id+"/rooms?inspected=false");assertThat(pending.path("total").asInt()).isEqualTo(1);
        assertThat(pending.path("items").get(0).path("totalScore").isNull()).isTrue();
        assertThat(get(manager,BASE+"/"+id+"/rooms?inspected=true").path("total").asInt()).isEqualTo(2);
        post(manager,BASE+"/"+id+"/rooms/"+pending.path("items").get(0).path("roomId").asLong()+"/score",scores(batch,100,100,100),201);
        assertThat(get(admin,BASE+"/"+id).path("completionRate").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(get(admin,BASE+"/"+id).path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void unfinishedExpectedRoomsBlockDeactivationAfterCheckoutUntilScored() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟应检停用保护"),201).path("id").asLong();
        var room=room(admin,building,"101","student");long roomId=room.path("id").asLong();
        var batch=post(manager,BASE+"/current/prepare",Map.of(),200);long id=batch.path("id").asLong();
        long residency=get(admin,"/api/housing/residencies?current=true").path("items").get(0).path("id").asLong();
        post(admin,"/api/housing/residencies/"+residency+"/checkout",Map.of("note","模拟退宿后仍应检查"),200);
        for(String path:List.of("/api/housing/buildings/"+building,"/api/housing/rooms/"+roomId)) {
            assertThat(post(admin,path+"/enabled",Map.of("enabled",false,"version",0),409).path("code").asText()).isEqualTo("UNFINISHED_INSPECTION");
        }
        post(manager,BASE+"/"+id+"/rooms/"+roomId+"/score",scores(batch,80,90,70),201);
        post(admin,"/api/housing/rooms/"+roomId+"/enabled",Map.of("enabled",false,"version",0),200);
        post(admin,"/api/housing/buildings/"+building+"/enabled",Map.of("enabled",false,"version",0),200);
        assertThat(get(manager,BASE+"/"+id+"/rooms/"+roomId).path("score").path("totalScore").decimalValue()).isEqualByComparingTo("80.00");
    }

    private record Fixture(Browser admin, Browser manager, JsonNode batch, JsonNode room) {
        String batchPath() { return BASE+"/"+batch.path("id").asLong(); }
        String roomPath() { return batchPath()+"/rooms/"+room.path("id").asLong(); }
    }
    private Fixture fixture() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟边界楼栋"),201).path("id").asLong();
        var room=room(admin,building,"101","student");
        return new Fixture(admin,manager,post(manager,BASE+"/current/prepare",Map.of(),200),room);
    }
    @Test
    void shanghaiIsoYearWeekAndMondayBelongingAreIndependentOfActualSubmissionTime() throws Exception {
        time.at("2020-12-31T16:30:00Z"); // Shanghai 2021-01-01 belongs to ISO 2020-W53.
        var f=fixture();assertThat(f.batch.path("isoYear").asInt()).isEqualTo(2020);assertThat(f.batch.path("isoWeek").asInt()).isEqualTo(53);
        assertThat(f.batch.path("weekStart").asText()).isEqualTo("2020-12-28");
        var first=post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),201);
        assertThat(first.path("submittedAt").asText()).isEqualTo("2020-12-31T16:30:00Z");
        time.at("2021-01-03T15:59:59Z");assertThat(get(f.manager,BASE+"/current").path("batch").path("id")).isEqualTo(f.batch.path("id"));
        time.at("2021-01-03T16:00:00Z");
        assertThat(get(f.manager,BASE+"/current").path("batch").isNull()).isTrue();
        assertThat(get(f.manager,f.batchPath()).path("scoringAllowed").asBoolean()).isFalse();
        assertThat(post(f.manager,f.roomPath()+"/score",scores(f.batch,100,100,100),409).path("code").asText()).isEqualTo("BATCH_NOT_OPEN");
        var next=post(f.admin,BASE+"/current/prepare",Map.of("isoYear",2099,"isoWeek",52),200);
        assertThat(next.path("isoYear").asInt()).isEqualTo(2021);assertThat(next.path("isoWeek").asInt()).isEqualTo(1);
        assertThat(next.path("weekStart").asText()).isEqualTo("2021-01-04");
        var nextScore=post(f.manager,BASE+"/"+next.path("id").asLong()+"/rooms/"+f.room.path("id").asLong()+"/score",scores(next,90,90,90),201);
        assertThat(nextScore.path("id")).isNotEqualTo(first.path("id"));
        assertThat(nextScore.path("submittedAt").asText()).isEqualTo("2021-01-03T16:00:00Z");
        assertThat(get(f.manager,f.roomPath()).path("score")).isEqualTo(first);
    }
    @Test
    void realBatchBindingAndSavedScoresKeepOldSchemeAndRoomSnapshotsAfterChanges() throws Exception {
        var f=fixture();long schemeId=f.batch.path("schemeId").asLong();
        var saved=post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),201);
        var copy=post(f.admin,"/api/hygiene/schemes/"+schemeId+"/copy",Map.of("version",1),201);long copyId=copy.path("id").asLong();
        var changed=Map.of("name","模拟新版本","threshold",75,"dimensions",List.of(Map.of("name","新维度","weight",100)));
        post(f.admin,"/api/hygiene/schemes/"+copyId,Map.of("version",0,"definition",changed),200);
        post(f.admin,"/api/hygiene/schemes/"+copyId+"/activate",Map.of("version",1,"activeId",schemeId),200);
        assertThat(post(f.admin,"/api/hygiene/schemes/"+schemeId,Map.of("version",2,"definition",changed),409).path("code").asText()).isEqualTo("SCHEME_FROZEN");
        long residency=get(f.admin,"/api/housing/residencies?current=true").path("items").get(0).path("id").asLong();
        post(f.admin,"/api/housing/residencies/"+residency+"/checkout",Map.of("note","模拟快照退宿"),200);
        long building=f.room.path("buildingId").asLong();var extra=room(f.admin,building,"102","student_new");
        post(f.admin,"/api/housing/buildings/"+building,Map.of("name","模拟改名楼栋","version",0),200);
        post(f.admin,"/api/housing/rooms/"+f.room.path("id").asLong(),Map.of("roomNo","改号101","floor",1,"version",0),200);
        var old=get(f.manager,f.batchPath());assertThat(old.path("schemeId")).isEqualTo(f.batch.path("schemeId"));
        assertThat(old.path("dimensions")).isEqualTo(f.batch.path("dimensions"));assertThat(old.path("expectedCount").asInt()).isEqualTo(1);
        var oldRoom=get(f.manager,f.roomPath());assertThat(oldRoom.path("room").path("buildingName").asText()).isEqualTo("模拟边界楼栋");
        assertThat(oldRoom.path("room").path("roomNo").asText()).isEqualTo("101");assertThat(oldRoom.path("score")).isEqualTo(saved);
        assertThat(post(f.manager,f.batchPath()+"/rooms/"+extra.path("id").asLong()+"/score",scores(f.batch,80,90,70),404).path("code").asText()).isEqualTo("NOT_EXPECTED_ROOM");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("DELETE FROM hygiene_scheme WHERE id=?",schemeId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("DELETE FROM hygiene_batch_room WHERE batch_id=?",f.batch.path("id").asLong())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        time.at("2026-11-23T01:00:00Z");var next=post(f.manager,BASE+"/current/prepare",Map.of(),200);
        assertThat(next.path("schemeId").asLong()).isEqualTo(copyId);assertThat(next.path("threshold").asInt()).isEqualTo(75);
        assertThat(get(f.manager,BASE+"/"+next.path("id").asLong()+"/rooms").path("items").get(0).path("roomId")).isEqualTo(extra.path("id"));
    }
    @Test
    void concurrentPreparationAndScoringHaveOneBatchAndOneWinnerWithDatabaseConstraints() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟竞争楼栋"),201).path("id").asLong();
        var room=room(admin,building,"101","student");
        post(admin,"/api/identity/accounts",Map.of("username","other_manager","displayName","模拟另一宿管","role","DORM_MANAGER","password",PASSWORD),201);
        var other=login("other_manager");admin.csrf();manager.csrf();other.csrf();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return post(admin,BASE+"/current/prepare",Map.of(),200);});
            var b=pool.submit(()->{start.await();return post(manager,BASE+"/current/prepare",Map.of(),200);});
            start.countDown();var batch=a.get();assertThat(b.get()).isEqualTo(batch);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_batch",Long.class)).isEqualTo(1);
            String path=BASE+"/"+batch.path("id").asLong()+"/rooms/"+room.path("id").asLong();
            var gate=new java.util.concurrent.CountDownLatch(1);
            var x=pool.submit(()->{gate.await();return manager.postJson(path+"/score",scores(batch,80,90,70));});
            var y=pool.submit(()->{gate.await();return other.postJson(path+"/score",scores(batch,100,100,100));});
            gate.countDown();var xr=x.get();var yr=y.get();assertThat(List.of(xr.statusCode(),yr.statusCode())).containsExactlyInAnyOrder(201,409);
            assertThat(xr.statusCode()==409?xr.body():yr.body()).contains("ALREADY_SCORED");
            var winner=get(admin,path).path("score");assertThat(winner.path("totalScore").asInt()).isEqualTo(xr.statusCode()==201?80:100);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='SCORE_SUBMITTED'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_score_dimension",Long.class)).isEqualTo(3);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("INSERT INTO hygiene_score(batch_id,room_id,total_score,threshold,submitted_by,submitted_at) SELECT batch_id,room_id,total_score,threshold,submitted_by,submitted_at FROM hygiene_score")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("INSERT INTO hygiene_batch(iso_year,iso_week,week_start,scheme_id,scheme_name,threshold,created_by,created_at) SELECT iso_year,iso_week,week_start,scheme_id,scheme_name,threshold,created_by,created_at FROM hygiene_batch")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {pool.shutdownNow();}
    }
    @Test
    void preparationAndScoreDetailOrAuditFailuresRollbackEntireBusinessTransaction() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟回滚楼栋"),201).path("id").asLong();
        var room=room(admin,building,"101","student");room(admin,building,"102","student_two");
        for(String table:List.of("hygiene_batch_room","operation_log")) {
            String predicate=table.equals("operation_log")?"action <> 'BATCH_PREPARED'":"room_no <> '102'";
            long before=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_batch CHECK ("+predicate+")");
            try {
                post(manager,BASE+"/current/prepare",Map.of(),500);assertThat(get(manager,BASE+"/current").path("batch").isNull()).isTrue();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_batch_room",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class)).isEqualTo(before);
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_batch");}
        }
        var batch=post(manager,BASE+"/current/prepare",Map.of(),200);String path=BASE+"/"+batch.path("id").asLong()+"/rooms/"+room.path("id").asLong();
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
        for(String table:List.of("hygiene_score_dimension","operation_log")) {
            String predicate=table.equals("operation_log")?"action <> 'SCORE_SUBMITTED'":"name <> '摆放'";
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_score CHECK ("+predicate+")");
            try {
                var error=post(manager,path+"/score",scores(batch,80,90,70),500);assertThat(error.toString()).doesNotContain("SQL","Exception","test_reject");
                assertThat(get(manager,path).path("score").isNull()).isTrue();assertThat(get(manager,BASE+"/"+batch.path("id").asLong()).path("inspectedCount").asInt()).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_score_dimension",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class)).isEqualTo(before);
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_score");}
        }
        var saved=post(manager,path+"/score",scores(batch,80,90,70),201);
        var audit=jdbc.queryForMap("SELECT actor_id,after_data FROM operation_log WHERE action='SCORE_SUBMITTED'");
        assertThat(((Number)audit.get("actor_id")).longValue()).isEqualTo(saved.path("submittedBy").asLong());
        assertThat(json.readTree(audit.get("after_data").toString())).isEqualTo(saved);
    }
    @Test
    void roleCsrfLiveSessionStateAndExactDimensionValidationCannotBeBypassed() throws Exception {
        var f=fixture();var student=login("student");var worker=login("maintainer");
        assertThat(new Browser().get(BASE+"/current").statusCode()).isEqualTo(401);
        for(var actor:List.of(student,worker)) {
            for(String path:List.of(BASE+"/current",f.batchPath(),f.batchPath()+"/rooms",f.roomPath())) assertThat(actor.get(path).statusCode()).isEqualTo(403);
            post(actor,BASE+"/current/prepare",Map.of(),403);post(actor,f.roomPath()+"/score",scores(f.batch,80,90,70),403);
        }
        post(f.admin,f.roomPath()+"/score",scores(f.batch,80,90,70),403);
        assertThat(f.manager.send(f.roomPath()+"/score","POST","application/json",json.writeValueAsString(scores(f.batch,80,90,70)),null).statusCode()).isEqualTo(403);
        assertThat(f.manager.send(BASE+"/current/prepare","POST","application/json","{}",null).statusCode()).isEqualTo(403);
        long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        for(Object invalid:List.of(-1,101,1.5,"bad")) post(f.manager,f.roomPath()+"/score",Map.of("dimensions",List.of(Map.of("dimensionId",dimension,"score",invalid))),400);
        for(Object invalid:List.of(Map.of(),Map.of("dimensions",List.of()),Map.of("dimensions",List.of(Map.of("dimensionId",dimension))),
                Map.of("dimensions",List.of(Map.of("dimensionId",dimension,"score",80))),
                Map.of("dimensions",List.of(Map.of("dimensionId",dimension,"score",80),Map.of("dimensionId",dimension,"score",90),Map.of("dimensionId",99999,"score",70))))) {
            post(f.manager,f.roomPath()+"/score",invalid,400);
        }
        for(String query:List.of("page=0","size=101","buildingId=0","inspected=bad","q="+"x".repeat(65))) assertThat(f.manager.get(f.batchPath()+"/rooms?"+query).statusCode()).isEqualTo(400);
        assertThat(f.manager.get(BASE+"/9999999").statusCode()).isEqualTo(404);
        for(String state:List.of("CLOSED","SUSPENDED")) {
            jdbc.update("UPDATE hygiene_batch SET status=? WHERE id=?",state,f.batch.path("id").asLong());
            try {assertThat(post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),409).path("code").asText()).isEqualTo("BATCH_NOT_OPEN");}
            finally {jdbc.update("UPDATE hygiene_batch SET status='OPEN' WHERE id=?",f.batch.path("id").asLong());}
        }
        assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isTrue();
        var account=f.admin.account("dorm_manager");post(f.admin,"/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("enabled",false,"version",0),200);
        assertThat(f.manager.get(BASE+"/current").statusCode()).isEqualTo(401);assertThat(f.manager.postJson(f.roomPath()+"/score",scores(f.batch,80,90,70)).statusCode()).isEqualTo(401);
    }
    @Test
    void emptyBatchHasNoPercentageAndQueriesSupportStablePaginationWithoutWrites() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");scheme(admin);
        var empty=post(admin,BASE+"/current/prepare",Map.of(),200);assertThat(empty.path("expectedCount").asInt()).isZero();assertThat(empty.path("completionRate").isNull()).isTrue();
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟分页楼栋"),201).path("id").asLong();
        room(admin,building,"101","student");room(admin,building,"102","student_two");room(admin,building,"103","student_three");
        assertThat(post(manager,BASE+"/current/prepare",Map.of(),200)).isEqualTo(empty);
        time.at("2026-11-23T01:00:00Z");var batch=post(manager,BASE+"/current/prepare",Map.of(),200);String path=BASE+"/"+batch.path("id").asLong()+"/rooms";
        long audits=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
        assertThat(get(manager,path+"?size=2").path("items").size()).isEqualTo(2);
        var second=get(manager,path+"?size=2&page=2&buildingId="+building);assertThat(second.path("total").asInt()).isEqualTo(3);assertThat(second.path("items").get(0).path("roomNo").asText()).isEqualTo("103");
        assertThat(get(manager,path+"?q=102").path("total").asInt()).isEqualTo(1);assertThat(get(manager,path+"?q=%25").path("total").asInt()).isZero();
        assertThat(get(manager,path+"?buildingId=999999").path("total").asInt()).isZero();assertThat(get(manager,path+"?page=9").path("items").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class)).isEqualTo(audits);
    }
    @Test
    void fractionalWeightHalfUpRoundingIsCalculatedOnServer() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        var scheme=post(admin,"/api/hygiene/schemes",Map.of("name","模拟舍入边界","dimensions",List.of(Map.of("name","甲","weight",0.5),Map.of("name","乙","weight",99.5))),201);
        post(admin,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        long building=post(admin,"/api/housing/buildings",Map.of("name","模拟精度楼栋"),201).path("id").asLong();
        var room=room(admin,building,"101","student");var batch=post(manager,BASE+"/current/prepare",Map.of(),200);
        var saved=post(manager,BASE+"/"+batch.path("id").asLong()+"/rooms/"+room.path("id").asLong()+"/score",scores(batch,1,0),201);
        assertThat(saved.path("totalScore").decimalValue()).isEqualByComparingTo("0.01");
    }

    @Test
    void preparingWhileActivatingAnotherSchemeBindsOneCompleteVersion() throws Exception {
        var f=fixture();long original=f.batch.path("schemeId").asLong();
        var draft=post(f.admin,"/api/hygiene/schemes/"+original+"/copy",Map.of("version",1),201);long id=draft.path("id").asLong();
        var next=post(f.admin,"/api/hygiene/schemes/"+id,Map.of("version",0,"definition",Map.of("name","模拟竞争新标准","threshold",75,"dimensions",List.of(Map.of("name","新维度","weight",100)))),200);
        time.at("2026-11-23T01:00:00Z");f.admin.csrf();f.manager.csrf();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var preparation=pool.submit(()->{gate.await();return post(f.manager,BASE+"/current/prepare",Map.of(),200);});
            var activation=pool.submit(()->{gate.await();return post(f.admin,"/api/hygiene/schemes/"+id+"/activate",Map.of("version",1,"activeId",original),200);});
            gate.countDown();var prepared=preparation.get();activation.get();
            assertThat(prepared.path("schemeId").asLong()).isIn(original,id);
            var definition=prepared.path("schemeId").asLong()==original?f.batch:next;
            assertThat(prepared.path("threshold")).isEqualTo(definition.path("threshold"));
            assertThat(prepared.path("dimensions")).isEqualTo(definition.path("dimensions"));
            assertThat(prepared.path("expectedCount").asInt()).isEqualTo(1);
            assertThat(post(f.manager,BASE+"/current/prepare",Map.of(),200)).isEqualTo(prepared);
            assertThat(get(f.manager,f.batchPath()).path("schemeId").asLong()).isEqualTo(original);
        } finally {pool.shutdownNow();}
    }

    @Test
    void personalPartialDraftSurvivesLoginAndNeverCountsAsFormalInspection() throws Exception {
        var f=fixture();long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        var input=Map.of("version",0,"dimensions",List.of(Map.of("dimensionId",dimension,"value","80.5")),"actorId",f.admin.account("system_admin").path("id").asLong());
        var saved=post(f.manager,f.roomPath()+"/draft",input,200);
        assertThat(saved.path("version").asLong()).isEqualTo(1);
        assertThat(saved.path("dimensions").get(0).path("value").asText()).isEqualTo("80.5");
        assertThat(get(f.manager,f.batchPath()).path("inspectedCount").asInt()).isZero();
        assertThat(get(f.admin,f.roomPath()).path("score").isNull()).isTrue();
        assertThat(f.manager.send("/api/auth/logout","POST",null,null,f.manager.csrf()).statusCode()).isEqualTo(204);
        var relogin=login("dorm_manager");
        assertThat(get(relogin,f.roomPath()+"/draft").path("draft")).isEqualTo(saved);
        assertThat(get(relogin,"/api/hygiene/drafts").path("items").get(0)).isEqualTo(saved);
        post(f.admin,"/api/identity/accounts",Map.of("username","other_manager","displayName","模拟另一宿管","role","DORM_MANAGER","password",PASSWORD),201);
        var other=login("other_manager");
        assertThat(get(other,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
        assertThat(get(other,"/api/hygiene/drafts").path("total").asInt()).isZero();
        post(other,f.roomPath()+"/draft",Map.of("version",0,"dimensions",List.of()),200);
        assertThat(get(relogin,f.roomPath()+"/draft").path("draft")).isEqualTo(saved);
    }

    private JsonNode saveDraft(Fixture f, Browser actor, long version, String value) throws Exception {
        return post(actor,f.roomPath()+"/draft",Map.of("version",version,"dimensions",List.of(Map.of("dimensionId",f.batch.path("dimensions").get(0).path("id").asLong(),"value",value))),200);
    }
    @Test
    void formalSubmissionClearsOnlyItsOwnDraftAndFailedOrConflictingSubmissionPreservesDrafts() throws Exception {
        var f=fixture();
        post(f.admin,"/api/identity/accounts",Map.of("username","other_manager","displayName","模拟另一宿管","role","DORM_MANAGER","password",PASSWORD),201);
        var other=login("other_manager");var mine=saveDraft(f,f.manager,0,"80");var theirs=saveDraft(f,other,0,"90");
        post(f.manager,f.roomPath()+"/score",scores(f.batch,80),400);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(mine);
        for(String table:List.of("hygiene_score_dimension","operation_log")) {
            String predicate=table.equals("operation_log")?"action <> 'SCORE_SUBMITTED'":"name <> '摆放'";
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT test_reject_draft_publish CHECK ("+predicate+")");
            try {
                post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),500);
                assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(mine);
                assertThat(get(f.admin,f.roomPath()).path("score").isNull()).isTrue();
                assertThat(get(f.manager,f.batchPath()).path("inspectedCount").asInt()).isZero();
            } finally {jdbc.execute("ALTER TABLE "+table+" DROP CHECK test_reject_draft_publish");}
        }
        var saved=post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),201);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
        assertThat(get(f.manager,"/api/hygiene/drafts").path("total").asInt()).isZero();
        assertThat(post(other,f.roomPath()+"/score",scores(f.batch,90,90,90),409).path("code").asText()).isEqualTo("ALREADY_SCORED");
        assertThat(get(other,f.roomPath()+"/draft").path("draft")).isEqualTo(theirs);
        assertThat(get(other,f.roomPath()).path("score")).isEqualTo(saved);
        assertThat(get(other,f.batchPath()).path("inspectedCount").asInt()).isEqualTo(1);
    }

    @Test
    void draftVersionRejectsStaleSaveAndPublishAndDraftAuditFailureRollsBack() throws Exception {
        var f=fixture();var first=saveDraft(f,f.manager,0,"80");
        long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        var update=Map.of("version",1,"dimensions",List.of(Map.of("dimensionId",dimension,"value","90")));
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_reject_draft CHECK (action <> 'SCORE_DRAFT_SAVED' OR before_data IS NULL)");
        try {
            post(f.manager,f.roomPath()+"/draft",update,500);
            assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(first);
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK test_reject_draft"); }
        var latest=post(f.manager,f.roomPath()+"/draft",update,200);
        assertThat(post(f.manager,f.roomPath()+"/draft",update,409).path("code").asText()).isEqualTo("DRAFT_CHANGED");
        var stale=json.valueToTree(scores(f.batch,80,90,70));((com.fasterxml.jackson.databind.node.ObjectNode)stale).put("draftVersion",1);
        assertThat(post(f.manager,f.roomPath()+"/score",stale,409).path("code").asText()).isEqualTo("DRAFT_CHANGED");
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(latest);
        assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)stale).put("draftVersion",2);
        post(f.manager,f.roomPath()+"/score",stale,201);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
    }

    @Test
    void failureToClearDraftRollsBackScoreAndRetryCanPublish() throws Exception {
        var f=fixture();var saved=saveDraft(f,f.manager,0,"80");
        jdbc.execute("CREATE TABLE test_draft_delete_guard (actor_id BIGINT,batch_id BIGINT,room_id BIGINT, FOREIGN KEY(actor_id,batch_id,room_id) REFERENCES hygiene_score_draft(actor_id,batch_id,room_id))");
        try {
            jdbc.execute("INSERT INTO test_draft_delete_guard SELECT actor_id,batch_id,room_id FROM hygiene_score_draft");
            post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),500);
            assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(saved);
            assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isTrue();
            assertThat(get(f.manager,f.batchPath()).path("inspectedCount").asInt()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hygiene_score_dimension",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='SCORE_SUBMITTED'",Long.class)).isZero();
        } finally { jdbc.execute("DROP TABLE test_draft_delete_guard"); }
        post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),201);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
    }
    @Test
    void draftPermissionsValidationAndClosedOrOldBatchCannotBypassFormalRules() throws Exception {
        var f=fixture();var draft=saveDraft(f,f.manager,0,"");long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        assertThat(new Browser().get("/api/hygiene/drafts").statusCode()).isEqualTo(401);
        for(var actor:List.of(f.admin,login("student"),login("maintainer"))) {
            assertThat(actor.get("/api/hygiene/drafts").statusCode()).isEqualTo(403);
            assertThat(actor.get(f.roomPath()+"/draft").statusCode()).isEqualTo(403);
            post(actor,f.roomPath()+"/draft",Map.of("version",1,"dimensions",List.of()),403);
        }
        assertThat(f.manager.send(f.roomPath()+"/draft","POST","application/json","{}",null).statusCode()).isEqualTo(403);
        for(Object bad:List.of(Map.of(),Map.of("version",-1,"dimensions",List.of()),
                Map.of("version",1,"dimensions",List.of(Map.of("dimensionId",dimension,"value","12345"))),
                Map.of("version",1,"dimensions",List.of(Map.of("dimensionId",999999,"value","80"))),
                Map.of("version",1,"dimensions",List.of(Map.of("dimensionId",dimension,"value","80"),Map.of("dimensionId",dimension,"value","90")))))
            post(f.manager,f.roomPath()+"/draft",bad,400);
        for(String query:List.of("page=0","size=101","size=0")) assertThat(f.manager.get("/api/hygiene/drafts?"+query).statusCode()).isEqualTo(400);
        assertThat(get(f.manager,"/api/hygiene/drafts?page=2&size=1").path("items").isEmpty()).isTrue();
        post(f.manager,f.batchPath()+"/rooms/999999/draft",Map.of("version",0,"dimensions",List.of()),404);
        for(String state:List.of("CLOSED","SUSPENDED")) {
            jdbc.update("UPDATE hygiene_batch SET status=? WHERE id=?",state,f.batch.path("id").asLong());
            try {
                assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").path("dimensions")).isEqualTo(draft.path("dimensions"));
                post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),409);
                post(f.manager,f.roomPath()+"/draft",Map.of("version",1,"dimensions",List.of()),409);
            } finally {jdbc.update("UPDATE hygiene_batch SET status='OPEN' WHERE id=?",f.batch.path("id").asLong());}
        }
        time.at("2026-11-23T01:00:00Z");
        assertThat(get(f.manager,"/api/hygiene/drafts").path("items").get(0)).isEqualTo(draft);
        post(f.manager,f.roomPath()+"/score",scores(f.batch,80,90,70),409);
        assertThat(get(f.manager,f.roomPath()+"/draft").path("draft")).isEqualTo(draft);
        assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isTrue();
        var account=f.admin.account("dorm_manager");post(f.admin,"/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("enabled",false,"version",0),200);
        assertThat(f.manager.get("/api/hygiene/drafts").statusCode()).isEqualTo(401);
        assertThat(f.manager.postJson(f.roomPath()+"/draft",Map.of("version",1,"dimensions",List.of())).statusCode()).isEqualTo(401);
    }
    @Test
    void simultaneousDraftUpdatesAndFormalSubmissionCannotLoseNewerInputOrResurrectDraft() throws Exception {
        var f=fixture();saveDraft(f,f.manager,0,"80");var second=login("dorm_manager");f.manager.csrf();second.csrf();
        long dimension=f.batch.path("dimensions").get(0).path("id").asLong();
        var update=Map.of("version",1,"dimensions",List.of(Map.of("dimensionId",dimension,"value","90")));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return f.manager.postJson(f.roomPath()+"/draft",update);});
            var b=pool.submit(()->{gate.await();return second.postJson(f.roomPath()+"/draft",update);});
            gate.countDown();assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
            var publish=json.valueToTree(scores(f.batch,80,90,70));((com.fasterxml.jackson.databind.node.ObjectNode)publish).put("draftVersion",2);
            var edit=Map.of("version",2,"dimensions",List.of(Map.of("dimensionId",dimension,"value","95")));
            var start=new java.util.concurrent.CountDownLatch(1);
            var save=pool.submit(()->{start.await();return f.manager.postJson(f.roomPath()+"/draft",edit);});
            var submit=pool.submit(()->{start.await();return second.postJson(f.roomPath()+"/score",publish);});
            start.countDown();var sr=save.get();var pr=submit.get();
            if(sr.statusCode()==200) {
                assertThat(pr.statusCode()).isEqualTo(409);assertThat(pr.body()).contains("DRAFT_CHANGED");
                assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").path("version").asLong()).isEqualTo(3);
                assertThat(get(f.manager,f.roomPath()).path("score").isNull()).isTrue();
            } else {
                assertThat(sr.statusCode()).isEqualTo(409);assertThat(pr.statusCode()).isEqualTo(201);
                assertThat(get(f.manager,f.roomPath()+"/draft").path("draft").isNull()).isTrue();
                assertThat(get(f.manager,f.batchPath()).path("inspectedCount").asInt()).isEqualTo(1);
            }
        } finally {pool.shutdownNow();}
    }

}
