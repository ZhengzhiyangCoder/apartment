package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class ManagementStatisticsHttpTest extends HttpIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired BatchHttpTest.MutableClock time;
    @org.junit.jupiter.api.BeforeEach void resetTime() { time.at("2026-11-16T01:00:00Z"); }
    private static final String RESULTS = "/api/hygiene/statistics";
    private Browser login(String name) throws Exception { var b=new Browser(); assertThat(b.login(name,PASSWORD).statusCode()).isEqualTo(200); return b; }
    private JsonNode get(Browser b,String path) throws Exception { var r=b.get(path); assertThat(r.statusCode()).as(r.body()).isEqualTo(200); return json.readTree(r.body()); }
    private JsonNode post(Browser b,String path,Object body,int status) throws Exception { var r=b.postJson(path,body); assertThat(r.statusCode()).as(r.body()).isEqualTo(status); return json.readTree(r.body()); }
    private record Room(long id,long bed,long residency) {}
    private Room room(Browser admin,long building,String number,String username) throws Exception {
        long room=post(admin,"/api/housing/rooms",Map.of("buildingId",building,"roomNo",number,"floor",1),201).path("id").asLong();
        long bed=post(admin,"/api/housing/beds",Map.of("roomId",room,"bedNo","01"),201).path("id").asLong();
        if(username==null) return new Room(room,bed,0);
        if(!username.equals("student")) post(admin,"/api/identity/accounts",Map.of("username",username,"displayName","不应出现在榜单的姓名","role","STUDENT","password",PASSWORD),201);
        long student=post(admin,"/api/housing/students",Map.of("userId",admin.account(username).path("id").asLong(),"studentNo",username,"fullName","不应出现在榜单的姓名","college","模拟学院","major","模拟专业","className","模拟班级"),201).path("id").asLong();
        long residency=post(admin,"/api/housing/residencies",Map.of("studentId",student,"bedId",bed),201).path("id").asLong();
        return new Room(room,bed,residency);
    }
    private long building(Browser admin,String name) throws Exception { return post(admin,"/api/housing/buildings",Map.of("name",name),201).path("id").asLong(); }
    private JsonNode scheme(Browser admin,String name,long active,int... weights) throws Exception {
        var dimensions=new ArrayList<Map<String,Object>>();for(int i=0;i<weights.length;i++) dimensions.add(Map.of("name","模拟维度"+(i+1),"weight",weights[i]));
        var s=post(admin,"/api/hygiene/schemes",Map.of("name",name,"threshold",65,"dimensions",dimensions),201);
        return post(admin,"/api/hygiene/schemes/"+s.path("id").asLong()+"/activate",Map.of("version",0,"activeId",active),200);
    }
    private JsonNode prepare(Browser manager,String instant) throws Exception { time.at(instant); return post(manager,"/api/hygiene/batches/current/prepare",Map.of(),200); }
    private void score(Browser manager,JsonNode batch,Room room,int... values) throws Exception {
        var dimensions=new ArrayList<Map<String,Object>>();for(int i=0;i<values.length;i++) dimensions.add(Map.of("dimensionId",batch.path("dimensions").get(i).path("id").asLong(),"score",values[i]));
        post(manager,"/api/hygiene/batches/"+batch.path("id").asLong()+"/rooms/"+room.id+"/score",Map.of("dimensions",dimensions),201);
    }
    private JsonNode state(Browser admin,JsonNode batch,String action) throws Exception {
        String path="/api/hygiene/batches/"+batch.path("id").asLong();
        return post(admin,path+"/state",Map.of("version",get(admin,path).path("version").asLong(),"action",action,"reason","模拟统计口径验证"),200);
    }

    @Test
    void buildingMeanWeightsRoomPeriodScoresEquallyAndCountsSnapshotProgress() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"模拟等权楼");var home=room(admin,a,"101","student");
        var peer=room(admin,a,"102","peer_student");var missing=room(admin,a,"103","missing_student");
        scheme(admin,"模拟等权标准",0,100);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,100);score(manager,w1,peer,0);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,100);
        var result=get(manager,RESULTS+"?period=MONTH&date=2026-11-30&buildingId="+a);
        var building=result.path("buildings").get(0);
        assertThat(building.path("score").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(building.path("participants").asInt()).isEqualTo(2);
        assertThat(building.path("samples").asInt()).isEqualTo(3);
        assertThat(building.path("progress").path("expected").asInt()).isEqualTo(6);
        assertThat(building.path("progress").path("inspected").asInt()).isEqualTo(3);
        assertThat(building.path("progress").path("missing").asInt()).isEqualTo(3);
        assertThat(building.path("progress").path("completionRate").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(get(admin,RESULTS+"?period=MONTH&date=2026-11-30&buildingId="+a)).isEqualTo(result);
        assertThat(login("student").get(RESULTS+"?buildingId="+a).statusCode()).isEqualTo(403);
        assertThat(login("maintainer").get(RESULTS).statusCode()).isEqualTo(403);
        assertThat(new Browser().get(RESULTS).statusCode()).isEqualTo(401);
    }
    @Test
    void versionDimensionsTrendsAndRoomDetailsShareEffectiveSamplesAndBuildingRanks() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"模拟A楼"),b=building(admin,"模拟B楼");
        var home=room(admin,a,"102","student");var peer=room(admin,a,"101","peer_student");var other=room(admin,b,"201","other_student");
        var v1=scheme(admin,"模拟版本一",0,100);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,80);score(manager,w1,peer,90);score(manager,w1,other,60);
        var v2=scheme(admin,"模拟版本二",v1.path("id").asLong(),50,50);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,100,80);score(manager,w2,peer,90,90);
        String query="?period=MONTH&date=2026-11-30";
        var all=get(manager,RESULTS+query);
        assertThat(all.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("87.50");
        assertThat(all.path("buildings").get(1).path("score").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(all.path("summary").path("participants").asInt()).isEqualTo(3);
        assertThat(all.path("summary").path("samples").asInt()).isEqualTo(5);
        assertThat(all.path("summary").has("score")).isFalse(); // No invented school-wide total.
        var groups=all.path("dimensions");assertThat(groups.size()).isEqualTo(3);
        var first=groups.get(0);assertThat(first.path("schemeId")).isEqualTo(v1.path("id"));
        assertThat(first.path("buildingId").asLong()).isEqualTo(a);
        assertThat(first.path("dimensions").get(0).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(first.path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("100");
        assertThat(first.path("dimensions").get(0).path("samples").asInt()).isEqualTo(2);
        var second=groups.get(2);assertThat(second.path("schemeId")).isEqualTo(v2.path("id"));
        assertThat(second.path("dimensions").get(0).path("score").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(second.path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("50");
        var points=all.path("trend").get(0).path("points");assertThat(points.size()).isEqualTo(12);
        assertThat(points.get(10).path("score").isNull()).isTrue();
        assertThat(points.get(11).path("score").decimalValue()).isEqualByComparingTo("87.50");
        assertThat(points.get(11).path("samples").asInt()).isEqualTo(4);
        var one=get(manager,RESULTS+query+"&roomId="+home.id+"&size=1");
        assertThat(one.path("scope").path("buildingId").asLong()).isEqualTo(a);
        assertThat(one.path("rankings").path("items").get(0).path("rank").asInt()).isEqualTo(2);
        assertThat(one.path("rankings").path("items").get(0).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(one.path("weeks").get(0).path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("100");
        assertThat(one.path("weeks").get(1).path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("50");
        assertThat(one.path("trend").get(0).path("points").get(11).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(one.path("summary").path("progress").path("expected").asInt()).isEqualTo(2);
        var paged=get(manager,RESULTS+query+"&buildingId="+a+"&size=1&page=2");
        assertThat(paged.path("rankings").path("items").get(0).path("roomNo").asText()).isEqualTo("102");
        assertThat(paged.path("rankings").path("total").asInt()).isEqualTo(2);
        var student=get(login("student"),"/api/hygiene/student/results"+query);
        assertThat(student.path("summary").path("score")).isEqualTo(one.path("rankings").path("items").get(0).path("score"));
        assertThat(student.path("summary").path("rank")).isEqualTo(one.path("rankings").path("items").get(0).path("rank"));
    }

    @Test
    void snapshotChangesSuspensionRestorationAndClosedBatchesUpdateEveryStatistic() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"模拟治理楼");var home=room(admin,a,"101","student");var peer=room(admin,a,"102","peer_student");var missing=room(admin,a,"103","missing_student");
        scheme(admin,"模拟治理标准",0,100);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,100);score(manager,w1,peer,0);
        String path="/api/hygiene/batches/"+w1.path("id").asLong();
        post(manager,path+"/rooms/"+missing.id+"/draft",Map.of("version",0,"dimensions",List.of(Map.of("dimensionId",w1.path("dimensions").get(0).path("id").asLong(),"value","87"))),200);
        post(admin,"/api/housing/residencies/"+missing.residency+"/checkout",Map.of("note","模拟退宿不改历史应检"),200);
        String query="?period=MONTH&date=2026-11-30&buildingId="+a;
        var before=get(manager,RESULTS+query);
        assertThat(before.path("summary").path("progress").path("expected").asInt()).isEqualTo(3);
        assertThat(before.path("summary").path("progress").path("completionRate").decimalValue()).isEqualByComparingTo("66.67");
        post(admin,path+"/rooms/"+missing.id+"/remove",Map.of("version",get(admin,path).path("version").asLong(),"reason","模拟实际名单调整"),200);
        assertThat(get(manager,RESULTS+query).path("summary").path("progress").path("expected").asInt()).isEqualTo(2);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,80);state(admin,w2,"CLOSE");state(admin,w1,"SUSPEND");
        var suspended=get(manager,RESULTS+query);
        assertThat(suspended.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(suspended.path("summary").path("progress").path("expected").asInt()).isEqualTo(2);
        assertThat(suspended.path("summary").path("progress").path("completionRate").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(suspended.path("dimensions").get(0).path("dimensions").get(0).path("samples").asInt()).isEqualTo(1);
        assertThat(suspended.path("rankings").path("total").asInt()).isEqualTo(1);
        assertThat(suspended.path("trend").get(0).path("points").get(11).path("score").decimalValue()).isEqualByComparingTo("80.00");
        var detail=get(manager,RESULTS+query+"&roomId="+home.id);
        assertThat(detail.path("weeks").get(0).path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(detail.path("weeks").get(0).path("totalScore").isNull()).isTrue();
        assertThat(detail.path("weeks").get(1).path("status").asText()).isEqualTo("INSPECTED");
        state(admin,w1,"RESTORE");var restored=get(manager,RESULTS+query);
        assertThat(restored.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(restored.path("summary").path("samples").asInt()).isEqualTo(3);
        assertThat(restored.path("summary").path("progress").path("completionRate").decimalValue()).isEqualByComparingTo("75.00");
        state(admin,w1,"SUSPEND");state(admin,w2,"SUSPEND");
        var empty=get(manager,RESULTS+query);
        assertThat(empty.path("buildings").get(0).path("score").isNull()).isTrue();
        assertThat(empty.path("summary").path("progress").path("expected").asInt()).isZero();
        assertThat(empty.path("summary").path("progress").path("completionRate").isNull()).isTrue();
        assertThat(empty.path("rankings").path("total").asInt()).isZero();assertThat(empty.path("dimensions").isEmpty()).isTrue();
        for(var point:empty.path("trend").get(0).path("points")) assertThat(point.path("score").isNull()).isTrue();
    }

    @Test
    void historicalEntryUsesIsoMondayAndCurrentScoreWithoutAddingSchoolTotal() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"模拟跨年楼");var home=room(admin,a,"101","student");var peer=room(admin,a,"102","peer_student");
        scheme(admin,"模拟跨年标准",0,100);
        var old=prepare(manager,"2026-12-28T01:00:00Z");score(manager,old,peer,100);
        var january=prepare(manager,"2027-01-04T01:00:00Z");score(manager,january,home,0);
        state(admin,old,"REOPEN");time.at("2027-02-01T01:00:00Z");score(manager,old,home,80);
        var cross=get(manager,RESULTS+"?date=2027-01-01&buildingId="+a);
        assertThat(cross.path("period").path("label").asText()).isEqualTo("2026年第53周");
        assertThat(cross.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("90.00");
        var december=get(manager,RESULTS+"?period=MONTH&date=2026-12-01&roomId="+home.id);
        assertThat(december.path("weeks").get(3).path("submittedAt").asText()).isEqualTo("2027-02-01T01:00:00Z");
        assertThat(december.path("summary").path("samples").asInt()).isEqualTo(1);
        var jan=get(manager,RESULTS+"?period=MONTH&date=2027-01-31&buildingId="+a);
        assertThat(jan.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(jan.path("trend").get(0).path("points").get(10).path("score").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(get(manager,RESULTS+"?period=MONTH&date=2027-02-01&buildingId="+a).path("summary").path("samples").asInt()).isZero();
    }

    @Test
    void scopeAndPaginationAreValidatedAndDisabledRoomsKeepHistoricalResults() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"模拟范围A楼"),b=building(admin,"模拟范围B楼");var home=room(admin,a,"101","student");
        assertThat(get(manager,RESULTS+"?roomId="+home.id).path("summary").path("progress").path("completionRate").isNull()).isTrue();
        for(var query:List.of("?period=YEAR","?date=bad","?date=1900-01-01","?page=0","?size=101","?size=0","?buildingId="+b+"&roomId="+home.id))
            assertThat(manager.get(RESULTS+query).statusCode()).as(query).isEqualTo(400);
        assertThat(manager.get(RESULTS+"?buildingId=99999999").statusCode()).isEqualTo(404);
        assertThat(manager.get(RESULTS+"?roomId=99999999").statusCode()).isEqualTo(404);
        scheme(admin,"模拟范围标准",0,100);var batch=prepare(manager,"2026-11-16T01:00:00Z");score(manager,batch,home,80);
        var old=get(manager,RESULTS+"?roomId="+home.id);
        post(admin,"/api/housing/residencies/"+home.residency+"/checkout",Map.of("note","模拟历史查询"),200);
        post(admin,"/api/housing/rooms/"+home.id+"/enabled",Map.of("version",0,"enabled",false),200);
        assertThat(get(manager,RESULTS+"?roomId="+home.id)).isEqualTo(old);
        var large=get(manager,RESULTS+"?page=2147483647&size=100");
        assertThat(large.path("rankings").path("items").isEmpty()).isTrue();assertThat(large.path("rankings").path("total").asInt()).isEqualTo(1);
        post(admin,"/api/identity/accounts/"+admin.account("dorm_manager").path("id").asLong()+"/reset-password",Map.of("version",0,"newPassword","AnotherPass2026!"),200);
        assertThat(manager.get(RESULTS).statusCode()).isEqualTo(401);
    }

    @Test
    void buildingAverageUsesRoundedRoomPeriodsAndRenamesNeverSplitSamples() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");
        long a=building(admin,"Z模拟原楼");var home=room(admin,a,"102","student");var peer=room(admin,a,"101","peer_student");
        scheme(admin,"模拟小数标准",0,1,99);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,81,80);score(manager,w1,peer,80,80);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,80,80);
        String query="?period=MONTH&date=2026-11-30";
        var half=get(manager,RESULTS+query);
        assertThat(half.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("80.01");
        post(admin,"/api/housing/buildings/"+a,Map.of("version",0,"name","A模拟新楼"),200);
        post(admin,"/api/housing/rooms/"+home.id,Map.of("version",0,"roomNo","103","floor",1),200);
        var w3=prepare(manager,"2026-11-16T01:00:00Z");score(manager,w3,home,80,80);
        var result=get(manager,RESULTS+query);
        assertThat(result.path("buildings").get(0).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(result.path("summary").path("participants").asInt()).isEqualTo(2);
        assertThat(result.path("summary").path("samples").asInt()).isEqualTo(4);
        assertThat(result.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","1");
        assertThat(result.path("rankings").path("items").findValuesAsText("roomNo")).containsExactly("101","103");
        assertThat(result.path("trend").get(0).path("buildingName").asText()).isEqualTo("A模拟新楼");
        assertThat(result.path("dimensions").get(0).path("buildingName").asText()).isEqualTo("A模拟新楼");
        assertThat(result.path("dimensions").get(0).path("dimensions").get(0).path("score").decimalValue()).isEqualByComparingTo("80.25");
    }

}
