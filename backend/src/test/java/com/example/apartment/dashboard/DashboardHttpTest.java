package com.example.apartment.dashboard;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardHttpTest extends HttpIntegrationTest {
    private Browser login(String name) throws Exception { var b=new Browser();assertThat(b.login(name,PASSWORD).statusCode()).isEqualTo(200);return b; }
    private JsonNode get(Browser b,String path) throws Exception { var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body()); }
    private JsonNode post(Browser b,String path,Object body,int status) throws Exception { var r=b.postJson(path,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body()); }

    @Test void managementRefreshIsReadOnlyAndUsesExistingQueueScopes() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        for(var actor:List.of(admin,manager)) {
            var before=get(actor,"/api/hygiene/batches");
            for(int i=0;i<2;i++) {
                var board=get(actor,"/api/dashboard");
                assertThat(board.path("inspection").path("batch").isNull()).isTrue();
                assertThat(board.path("repairs")).hasSize(3);
                assertThat(board.path("repairs").get(0).path("page").path("total").asLong()).isZero();
                assertThat(board.path("rectifications").path("total").asLong()).isZero();
                assertThat(board.path("residency").isNull()).isTrue();
            }
            assertThat(get(actor,"/api/hygiene/batches")).isEqualTo(before);
        }
        assertThat(new Browser().get("/api/dashboard").statusCode()).isEqualTo(401);
    }

    @Test void studentRefreshFollowsCurrentResidenceAndCorrectedScoreButKeepsOwnOldOrders() throws Exception {
        var a=login("system_admin");var m=login("dorm_manager");var student=login("student");
        var empty=get(student,"/api/dashboard");assertThat(empty.path("residency").isNull()).isTrue();
        assertThat(empty.path("results").isNull()).isTrue();assertThat(empty.path("inspection").isNull()).isTrue();
        var f=house(a);var home=get(student,"/api/dashboard");
        assertThat(home.path("residency").path("roomId").asLong()).isEqualTo(f.room);
        assertThat(home.path("results").path("summary").path("score").isNull()).isTrue();
        var scheme=post(a,"/api/hygiene/schemes",Map.of("name","模拟工作台标准","dimensions",List.of(Map.of("name","地面","weight",100))),201);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        var batch=post(m,"/api/hygiene/batches/current/prepare",Map.of(),200);
        var values=List.of(Map.of("dimensionId",batch.path("dimensions").get(0).path("id").asLong(),"score",50));
        var score=post(m,"/api/hygiene/batches/"+batch.path("id").asLong()+"/rooms/"+f.room+"/score",Map.of("dimensions",values),201);
        assertThat(get(student,"/api/dashboard").path("results").path("summary").path("score").decimalValue()).isEqualByComparingTo("50");
        var management=get(m,"/api/dashboard");assertThat(management.path("inspection").path("batch").path("inspectedCount").asInt()).isEqualTo(1);
        assertThat(management.path("rectifications").path("total").asInt()).isEqualTo(1);
        post(a,"/api/hygiene/scores/"+score.path("id").asLong()+"/corrections",Map.of("version",0,"reason","核对为90分","dimensions",List.of(Map.of("dimensionId",batch.path("dimensions").get(0).path("id").asLong(),"score",90))),200);
        var updated=get(student,"/api/dashboard");assertThat(updated.path("results").path("summary").path("score").decimalValue()).isEqualByComparingTo("90");
        assertThat(updated.path("results").path("summary")).isEqualTo(get(student,"/api/hygiene/student/results").path("summary"));
        var order=submit(student);
        long nextRoom=post(a,"/api/housing/rooms",Map.of("buildingId",f.building,"roomNo","102","floor",1),201).path("id").asLong();
        long nextBed=post(a,"/api/housing/beds",Map.of("roomId",nextRoom,"bedNo","01"),201).path("id").asLong();
        var moved=post(a,"/api/housing/residencies/"+f.residency+"/transfer",Map.of("bedId",nextBed,"note","模拟调寝"),200);
        var after=get(student,"/api/dashboard?roomId="+f.room+"&role=SYSTEM_ADMIN");
        assertThat(after.path("role").asText()).isEqualTo("STUDENT");assertThat(after.path("residency").path("roomId").asLong()).isEqualTo(nextRoom);
        assertThat(after.path("results").path("summary").path("score").isNull()).isTrue();
        assertThat(after.path("repairs").get(0).path("page").path("items").get(0).path("id")).isEqualTo(order.path("id"));
        post(a,"/api/housing/residencies/"+moved.path("id").asLong()+"/checkout",Map.of("note","模拟退宿"),200);
        after=get(student,"/api/dashboard");assertThat(after.path("residency").isNull()).isTrue();assertThat(after.path("results").isNull()).isTrue();
        assertThat(after.path("repairs").get(0).path("page").path("total").asInt()).isEqualTo(1);
    }
    private record House(long building,long room,long residency) {}
    private House house(Browser a) throws Exception {
        long b=post(a,"/api/housing/buildings",Map.of("name","模拟工作台楼"),201).path("id").asLong();
        long r=post(a,"/api/housing/rooms",Map.of("buildingId",b,"roomNo","101","floor",1),201).path("id").asLong();
        long bed=post(a,"/api/housing/beds",Map.of("roomId",r,"bedNo","01"),201).path("id").asLong();
        long student=post(a,"/api/housing/students",Map.of("userId",a.account("student").path("id").asLong(),"studentNo","dashboard_student","fullName","模拟学生","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        long res=post(a,"/api/housing/residencies",Map.of("studentId",student,"bedId",bed),201).path("id").asLong();
        return new House(b,r,res);
    }
    private JsonNode submit(Browser student) throws Exception {
        String boundary="dashboard"+UUID.randomUUID();var body=new StringBuilder();
        for(var e:Map.of("category","PLUMBING","description","模拟工作台水龙头故障","requestId",UUID.randomUUID().toString()).entrySet())
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(e.getKey()).append("\"\r\n\r\n").append(e.getValue()).append("\r\n");
        body.append("--").append(boundary).append("--\r\n");
        var response=student.send("/api/repairs","POST","multipart/form-data; boundary="+boundary,body.toString(),student.csrf());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return json.readTree(response.body());
    }

    @Test void maintainerQueuesFollowAssignmentStateAndSessionRevocation() throws Exception {
        var a=login("system_admin");var m=login("dorm_manager");var w=login("maintainer");var student=login("student");house(a);
        var empty=get(w,"/api/dashboard");assertThat(empty.path("repairs")).hasSize(2);
        assertThat(empty.path("inspection").isNull()).isTrue();assertThat(empty.path("results").isNull()).isTrue();
        var order=submit(student);String path="/api/repairs/"+order.path("id").asLong();
        var worker=a.account("maintainer");
        post(a,"/api/identity/accounts",Map.of("username","other_worker","displayName","模拟其他维修","role","MAINTAINER","password",PASSWORD),201);
        long otherId=a.account("other_worker").path("id").asLong();var other=login("other_worker");
        post(m,path+"/assign",Map.of("version",0,"assigneeId",worker.path("id").asLong()),200);
        assertThat(get(w,"/api/dashboard").path("repairs").get(0).path("page").path("total").asInt()).isEqualTo(1);
        assertThat(get(other,"/api/dashboard?assigneeId="+worker.path("id").asLong()).path("repairs").get(0).path("page").path("total").asInt()).isZero();
        post(m,path+"/reassign",Map.of("version",1,"assigneeId",otherId,"reason","模拟转派"),200);
        assertThat(get(w,"/api/dashboard").path("repairs").get(0).path("page").path("total").asInt()).isZero();
        post(other,path+"/start",Map.of("version",2),200);
        var started=get(other,"/api/dashboard");assertThat(started.path("repairs").get(0).path("page").path("total").asInt()).isZero();
        assertThat(started.path("repairs").get(1).path("page")).isEqualTo(get(other,"/api/repairs?status=IN_PROGRESS&size=5"));
        post(other,path+"/complete",Map.of("version",3,"completionNote","模拟修复完成"),200);
        assertThat(get(other,"/api/dashboard").path("repairs").get(1).path("page").path("total").asInt()).isZero();
        assertThat(get(student,"/api/dashboard").path("repairs").get(0).path("page").path("items").get(0).path("status").asText()).isEqualTo("COMPLETED");
        var account=a.account("dorm_manager");post(a,"/api/identity/accounts/"+account.path("id").asLong()+"/role",Map.of("version",account.path("version").asLong(),"role","MAINTAINER"),200);
        assertThat(m.get("/api/dashboard").statusCode()).isEqualTo(401);
        var changed=get(login("dorm_manager"),"/api/dashboard");assertThat(changed.path("role").asText()).isEqualTo("MAINTAINER");assertThat(changed.path("rectifications").isNull()).isTrue();
    }

    @Test void queueTotalsAreNotTruncatedAndUnoccupiedPreparedBatchHasNoCompletionRate() throws Exception {
        var a=login("system_admin");var s=login("student");
        var scheme=post(a,"/api/hygiene/schemes",Map.of("name","模拟无应检方案","dimensions",List.of(Map.of("name","地面","weight",100))),201);
        post(a,"/api/hygiene/schemes/"+scheme.path("id").asLong()+"/activate",Map.of("version",0,"activeId",0),200);
        post(a,"/api/hygiene/batches/current/prepare",Map.of(),200);
        var current=get(a,"/api/dashboard").path("inspection").path("batch");
        assertThat(current.path("expectedCount").asInt()).isZero();assertThat(current.path("completionRate").isNull()).isTrue();
        house(a);for(int i=0;i<7;i++) submit(s);
        var queue=get(a,"/api/dashboard").path("repairs").get(0).path("page");assertThat(queue.path("total").asInt()).isEqualTo(7);assertThat(queue.path("items")).hasSize(5);
        assertThat(queue).isEqualTo(get(a,"/api/repairs?status=PENDING_ASSIGNMENT&size=5"));
        post(a,"/api/identity/accounts",Map.of("username","other_student","displayName","模拟未入住学生","role","STUDENT","password",PASSWORD),201);
        var other=login("other_student");assertThat(get(other,"/api/dashboard?submittedBy="+a.account("student").path("id").asLong()).path("repairs").get(0).path("page").path("total").asInt()).isZero();
        var account=a.account("other_student");post(a,"/api/identity/accounts/"+account.path("id").asLong()+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(other.get("/api/dashboard").statusCode()).isEqualTo(401);
    }
}
