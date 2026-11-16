package com.example.apartment.audit;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditHttpTest extends HttpIntegrationTest {
    private static final String AUDIT="/api/audit/records";
    private Browser login(String name) throws Exception { var b=new Browser();assertThat(b.login(name,PASSWORD).statusCode()).isEqualTo(200);return b; }
    private JsonNode get(Browser b,String path) throws Exception { var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body()); }
    private JsonNode post(Browser b,String path,Object body,int status) throws Exception { var r=b.postJson(path,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body()); }
    @Test void administratorFindsActualAccountChangesWithoutCredentialsAndOtherRolesCannotReadThem() throws Exception {
        var admin=login("system_admin");
        var account=post(admin,"/api/identity/accounts",Map.of("username","audit_student","displayName","模拟审计学生","role","STUDENT","password",PASSWORD),201);
        post(admin,"/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),200);
        var list=get(admin,AUDIT+"?targetType=ACCOUNT&targetId="+account.path("id").asLong());
        assertThat(list.path("total").asInt()).isEqualTo(2);
        var entry=list.path("items").get(0);assertThat(entry.path("action").asText()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(entry.path("actorName").asText()).isEqualTo("模拟SYSTEM_ADMIN");
        assertThat(entry.path("createdAt").asText()).isNotBlank();
        String detailPath=AUDIT+"/"+entry.path("id").asLong();var detail=get(admin,detailPath);
        assertThat(detail.path("before").path("enabled").asBoolean()).isTrue();
        assertThat(detail.path("after").path("enabled").asBoolean()).isFalse();
        assertThat(detail.toString()).doesNotContain(PASSWORD,"password","authVersion","session","hash");
        assertThat(list.toString()).doesNotContain("before","after","模拟审计学生");
        assertThat(detail.path("references").get(0).path("path").asText()).isEqualTo("/accounts?record="+account.path("id").asLong());
        assertThat(get(login("dorm_manager"),AUDIT).path("total").asInt()).isZero();
        assertThat(login("dorm_manager").get(detailPath).statusCode()).isEqualTo(404);
        for(String name:List.of("student","maintainer")) {
            var other=login(name);assertThat(other.get(AUDIT).statusCode()).isEqualTo(403);assertThat(other.get(detailPath).statusCode()).isEqualTo(403);
        }
        assertThat(new Browser().get(AUDIT).statusCode()).isEqualTo(401);
        assertThat(admin.postJson(detailPath,Map.of("action","CHANGED")).statusCode()).isEqualTo(403);
    }
    private JsonNode one(Browser b,String action) throws Exception {
        var list=get(b,AUDIT+"?action="+action);assertThat(list.path("total").asInt()).isGreaterThan(0);
        return get(b,AUDIT+"/"+list.path("items").get(0).path("id").asLong());
    }
    @Test void tracesResidencyRepairRosterAndCorrectionThroughExistingBusinessHistories() throws Exception {
        var a=login("system_admin");var m=login("dorm_manager");var student=login("student");
        long building=post(a,"/api/housing/buildings",Map.of("name","模拟审计楼"),201).path("id").asLong();
        long room=post(a,"/api/housing/rooms",Map.of("buildingId",building,"roomNo","101","floor",1),201).path("id").asLong();
        long room2=post(a,"/api/housing/rooms",Map.of("buildingId",building,"roomNo","102","floor",1),201).path("id").asLong();
        long bed=post(a,"/api/housing/beds",Map.of("roomId",room,"bedNo","01"),201).path("id").asLong();
        long bed2=post(a,"/api/housing/beds",Map.of("roomId",room2,"bedNo","02"),201).path("id").asLong();
        long profile=post(a,"/api/housing/students",Map.of("userId",a.account("student").path("id").asLong(),"studentNo","audit_001","fullName","模拟学生","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        long residency=post(a,"/api/housing/residencies",Map.of("studentId",profile,"bedId",bed),201).path("id").asLong();
        String boundary="audit-boundary";String form="--"+boundary+"\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\nOTHER\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"description\"\r\n\r\n模拟敏感描述无需在管理审计重复展示\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+UUID.randomUUID()+"\r\n--"+boundary+"--\r\n";
        var submitted=student.send("/api/repairs","POST","multipart/form-data; boundary="+boundary,form,student.csrf());assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(201);
        long order=json.readTree(submitted.body()).path("id").asLong();String repair="/api/repairs/"+order;
        var worker=post(a,"/api/identity/accounts",Map.of("username","audit_worker","displayName","模拟替班","role","MAINTAINER","password",PASSWORD),201);
        post(m,repair+"/assign",Map.of("assigneeId",a.account("maintainer").path("id").asLong(),"version",0),200);
        post(m,repair+"/reassign",Map.of("assigneeId",worker.path("id").asLong(),"version",1,"reason","模拟原维修人员请假"),200);
        var reassigned=one(m,"REPAIR_REASSIGNED");assertThat(reassigned.path("reason").asText()).isEqualTo("模拟原维修人员请假");
        assertThat(reassigned.path("before").path("assigneeName").asText()).isEqualTo("模拟MAINTAINER");
        assertThat(reassigned.path("after").path("assigneeName").asText()).isEqualTo("模拟替班");
        assertThat(reassigned.toString()).doesNotContain("模拟敏感描述","studentName","images","logs","requestId");
        post(a,"/api/housing/residencies/"+residency+"/transfer",Map.of("bedId",bed2,"note","模拟就近调寝"),200);
        var transfer=one(a,"RESIDENCY_TRANSFERRED");assertThat(transfer.path("reason").asText()).isEqualTo("模拟就近调寝");
        assertThat(transfer.path("after").path("current").path("roomNo").asText()).isEqualTo("102");
        assertThat(transfer.path("references").size()).isEqualTo(2);
        var scheme=post(a,"/api/hygiene/schemes",Map.of("name","模拟审计标准","dimensions",List.of(Map.of("name","地面","weight",100))),201);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        var batch=post(m,"/api/hygiene/batches/current/prepare",Map.of(),200);String batchPath="/api/hygiene/batches/"+batch.path("id").asLong();
        post(a,batchPath+"/rooms/"+room2+"/remove",Map.of("version",0,"reason","模拟调整名单"),200);
        var removed=one(a,"BATCH_ROOM_REMOVED");assertThat(removed.path("reason").asText()).isEqualTo("模拟调整名单");
        assertThat(removed.path("before").path("room").path("roomId").asLong()).isEqualTo(room2);assertThat(removed.path("after").path("room").isNull()).isTrue();
        post(a,batchPath+"/rooms/"+room2+"/add",Map.of("version",1,"reason","模拟恢复名单"),200);
        long dimension=batch.path("dimensions").get(0).path("id").asLong();
        post(m,batchPath+"/rooms/"+room2+"/draft",Map.of("version",0,"dimensions",List.of(Map.of("dimensionId",dimension,"value","17"))),200);
        var draft=one(a,"SCORE_DRAFT_SAVED");assertThat(draft.path("after").path("roomId").asLong()).isEqualTo(room2);
        assertThat(draft.toString()).doesNotContain("dimensions","value");
        var score=post(m,batchPath+"/rooms/"+room2+"/score",Map.of("dimensions",List.of(Map.of("dimensionId",dimension,"score",80))),201);
        String changes="/api/hygiene/scores/"+score.path("id").asLong()+"/corrections";
        post(a,changes,Map.of("version",0,"reason","模拟复核为50","dimensions",List.of(Map.of("dimensionId",dimension,"score",50))),200);
        var correction=one(a,"SCORE_CORRECTED");assertThat(correction.path("reason").asText()).isEqualTo("模拟复核为50");
        assertThat(correction.path("before").path("totalScore").asInt()).isEqualTo(80);assertThat(correction.path("after").path("totalScore").asInt()).isEqualTo(50);
        assertThat(correction.path("after").path("dimensions").get(0).path("score").asInt()).isEqualTo(50);
        assertThat(get(a,changes).path("items").get(0).path("reason")).isEqualTo(correction.path("reason"));
        assertThat(one(a,"RECTIFICATION_TRIGGERED").path("after").path("score").asInt()).isEqualTo(50);
        var own=get(m,AUDIT+"?size=100");assertThat(own.path("scope").asText()).isEqualTo("OWN");
        for(var entry:own.path("items")) assertThat(entry.path("actorId").asLong()).isEqualTo(a.account("dorm_manager").path("id").asLong());
        assertThat(m.get(AUDIT+"?actorId="+a.account("system_admin").path("id").asLong()).statusCode()).isEqualTo(403);
        assertThat(m.get(AUDIT+"/"+correction.path("entry").path("id").asLong()).statusCode()).isEqualTo(404);
        assertThat(get(student,repair).path("logs").size()).isEqualTo(3);
        assertThat(login("maintainer").get(repair).statusCode()).isEqualTo(404);
        // Active business references block deactivation; failures must not acquire success audit rows.
        long total=get(a,AUDIT).path("total").asLong();
        post(a,"/api/housing/beds/"+bed+"/enabled",Map.of("version",0,"enabled",false),409);
        post(a,"/api/identity/accounts/"+worker.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),409);
        post(a,"/api/housing/rooms/"+room2+"/enabled",Map.of("version",0,"enabled",false),409);
        assertThat(get(a,AUDIT).path("total").asLong()).isEqualTo(total);
        var assigned=login("audit_worker");post(assigned,repair+"/start",Map.of("version",2),200);
        post(assigned,repair+"/complete",Map.of("version",3,"completionNote","模拟完成维修并保留处理说明"),200);
        assertThat(one(a,"REPAIR_COMPLETED").path("reason").asText()).isEqualTo("模拟完成维修并保留处理说明");
        assertThat(one(a,"REPAIR_STARTED").path("after").path("status").asText()).isEqualTo("IN_PROGRESS");
        var event=get(a,"/api/hygiene/rectifications").path("items").get(0);String eventPath="/api/hygiene/rectifications/"+event.path("id").asLong();
        post(m,eventPath+"/follow-ups",Map.of("version",event.path("version").asLong(),"note","模拟保留整改跟进"),200);
        post(a,changes,Map.of("version",1,"reason","模拟复核为90","dimensions",List.of(Map.of("dimensionId",dimension,"score",90))),200);
        assertThat(one(a,"RECTIFICATION_CORRECTED").path("after").path("status").asText()).isEqualTo("OPEN");
        var verified=post(m,eventPath+"/verify",Map.of("version",get(m,eventPath).path("version").asLong(),"note","模拟重新核实"),200);
        post(m,eventPath+"/close",Map.of("version",verified.path("version").asLong(),"note","模拟核实后关闭"),200);
        assertThat(one(m,"RECTIFICATION_FOLLOW_UP").path("reason").asText()).isEqualTo("模拟保留整改跟进");
        assertThat(one(m,"RECTIFICATION_VERIFIED").path("reason").asText()).isEqualTo("模拟重新核实");
        assertThat(one(m,"RECTIFICATION_CLOSED").path("after").path("status").asText()).isEqualTo("CLOSED");
        long current=transfer.path("after").path("current").path("id").asLong();
        post(a,"/api/housing/residencies/"+current+"/checkout",Map.of("note","模拟离校退宿"),200);
        post(a,"/api/housing/beds/"+bed2+"/enabled",Map.of("version",0,"enabled",false),200);
        post(a,"/api/housing/students/"+profile+"/enabled",Map.of("version",0,"enabled",false),200);
        post(a,"/api/identity/accounts/"+worker.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(one(a,"RESIDENCY_CHECKED_OUT").path("reason").asText()).isEqualTo("模拟离校退宿");
        assertThat(get(a,"/api/housing/residencies/"+residency).path("roomNo").asText()).isEqualTo("101");
        assertThat(get(a,"/api/housing/residencies/"+current).path("roomNo").asText()).isEqualTo("102");
        assertThat(get(a,"/api/housing/beds/"+bed2).path("enabled").asBoolean()).isFalse();
        assertThat(get(a,"/api/housing/students/"+profile).path("enabled").asBoolean()).isFalse();
        assertThat(get(a,repair).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(get(a,changes).path("total").asInt()).isEqualTo(2);
        assertThat(one(a,"BED_DISABLED").path("after").path("bedNo").asText()).isEqualTo("02");
        assertThat(one(a,"STUDENT_DISABLED").path("after").path("studentNo").asText()).isEqualTo("audit_001");

    }

    @Test void validatesFiltersAndUsesStableTimePaginationWithoutLeakingPayloads() throws Exception {
        var a=login("system_admin");
        for(int i=0;i<12;i++) post(a,"/api/housing/buildings",Map.of("name","模拟筛选楼"+i),201);
        var newest=get(a,AUDIT+"?targetType=BUILDING&sort=NEWEST&size=5");
        var oldest=get(a,AUDIT+"?targetType=BUILDING&sort=OLDEST&size=100");
        assertThat(newest.path("total").asInt()).isEqualTo(12);assertThat(newest.path("items").size()).isEqualTo(5);
        assertThat(get(a,AUDIT+"?page=3&size=5").path("items").size()).isEqualTo(2);
        assertThat(newest.path("items").get(0)).isEqualTo(oldest.path("items").get(11));
        var first=oldest.path("items").get(0);var last=oldest.path("items").get(11);
        String period="?from="+first.path("createdAt").asText()+"&until="+last.path("createdAt").asText();
        assertThat(get(a,AUDIT+period).path("total").asInt()).isEqualTo(11);
        assertThat(get(a,AUDIT+"?actorId="+first.path("actorId").asLong()+"&action=BUILDING_CREATED").path("total").asInt()).isEqualTo(12);
        assertThat(get(a,AUDIT+"?targetId="+first.path("targetId").asLong()+"&targetType=ROOM").path("total").asInt()).isZero();
        assertThat(get(a,AUDIT+"?page=2147483647&size=100").path("items").isEmpty()).isTrue();
        for(String q:List.of("page=0","size=0","size=101","actorId=-1","targetId=0","targetType=SQL","sort=id","action=INVALID","from=bad", "from=2026-11-16T00:00:00Z&until=2026-11-16T00:00:00Z"))
            assertThat(a.get(AUDIT+"?"+q).statusCode()).as(q).isEqualTo(400);
        assertThat(a.get(AUDIT+"/9223372036854775807").statusCode()).isEqualTo(404);
    }

    @Test void stableReferencesStillResolveRenamedDisabledObjectsAndRejectUnauthorizedSourceReads() throws Exception {
        var a=login("system_admin");var m=login("dorm_manager");
        var building=post(a,"/api/housing/buildings",Map.of("name","模拟原楼名"),201);long id=building.path("id").asLong();
        var original=one(a,"BUILDING_CREATED");String detail=AUDIT+"/"+original.path("entry").path("id").asLong();
        post(a,"/api/housing/buildings/"+id,Map.of("name","模拟现楼名","version",0),200);
        post(a,"/api/housing/buildings/"+id+"/enabled",Map.of("enabled",false,"version",1),200);
        var now=get(a,"/api/housing/buildings/"+id);assertThat(now.path("name").asText()).isEqualTo("模拟现楼名");assertThat(now.path("enabled").asBoolean()).isFalse();
        assertThat(get(a,detail).path("after").path("name").asText()).isEqualTo("模拟原楼名");
        assertThat(get(m,"/api/housing/buildings/"+id)).isEqualTo(now);
        var target=a.account("disabled");assertThat(get(a,"/api/identity/accounts/"+target.path("id").asLong()).path("enabled").asBoolean()).isFalse();
        assertThat(m.get("/api/identity/accounts/"+target.path("id").asLong()).statusCode()).isEqualTo(403);
        for(String kind:List.of("buildings","rooms","beds","students","residencies")) {
            assertThat(a.get("/api/housing/"+kind+"/9223372036854775807").statusCode()).isEqualTo(404);
            assertThat(login("student").get("/api/housing/"+kind+"/"+id).statusCode()).isEqualTo(403);
        }
    }

    @Test void failedBusinessWritesNeverProduceSuccessAuditAndDeactivatedActorsRemainTraceable() throws Exception {
        var a=login("system_admin");var manager=login("dorm_manager");
        long building=post(a,"/api/housing/buildings",Map.of("name","模拟事务楼"),201).path("id").asLong();
        var before=get(a,AUDIT);var initial=get(a,"/api/housing/buildings/"+building);
        post(a,"/api/housing/buildings/"+building,Map.of("name","过期版本失败","version",99),409);
        jdbc.execute("ALTER TABLE operation_log ADD CONSTRAINT test_audit_failure CHECK (action <> 'BUILDING_UPDATED')");
        try {
            post(a,"/api/housing/buildings/"+building,Map.of("name","模拟存储失败","version",0),500);
            assertThat(get(a,"/api/housing/buildings/"+building)).isEqualTo(initial);
            assertThat(get(a,AUDIT)).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE operation_log DROP CHECK test_audit_failure"); }
        var scheme=post(a,"/api/hygiene/schemes",Map.of("name","模拟审计留痕方案","dimensions",List.of(Map.of("name","地面","weight",100))),201);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        post(manager,"/api/hygiene/batches/current/prepare",Map.of(),200);var prepared=one(a,"BATCH_PREPARED");
        var account=a.account("dorm_manager");
        post(a,"/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(manager.get(AUDIT).statusCode()).isEqualTo(401);
        assertThat(get(a,AUDIT+"/"+prepared.path("entry").path("id").asLong())).isEqualTo(prepared);
        assertThat(get(a,AUDIT+"?actorId="+account.path("id").asLong()).path("total").asInt()).isEqualTo(1);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong(),Map.of("version",1,"definition",Map.of("name","非法覆写已用方案","dimensions",List.of(Map.of("name","地面","weight",100)))),409);
        assertThat(get(a,AUDIT+"?action=SCHEME_UPDATED").path("total").asInt()).isZero();
        var admin=a.account("system_admin");post(a,"/api/identity/accounts/"+admin.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),409);
        assertThat(get(a,AUDIT+"?action=ACCOUNT_DISABLED").path("total").asInt()).isEqualTo(1);
    }

}
