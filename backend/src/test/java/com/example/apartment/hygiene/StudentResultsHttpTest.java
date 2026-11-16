package com.example.apartment.hygiene;

import com.example.apartment.testing.HttpIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.context.annotation.Import(BatchHttpTest.TimeControl.class)
class StudentResultsHttpTest extends HttpIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired BatchHttpTest.MutableClock time;
    @org.junit.jupiter.api.BeforeEach void resetTime() { time.at("2026-11-16T01:00:00Z"); }
    private static final String RESULTS = "/api/hygiene/student/results";
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
    void isoYearMondayMonthAndLateHistoricalEntryKeepOriginalPeriod() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");var student=login("student");
        long building=building(admin,"模拟跨年楼");var home=room(admin,building,"101","student");
        var other=room(admin,building,"102","second_student");scheme(admin,"模拟跨年标准",0,100);
        var cross=prepare(manager,"2026-12-28T01:00:00Z");score(manager,cross,other,90);
        prepare(manager,"2027-01-04T01:00:00Z");state(admin,cross,"REOPEN");
        time.at("2027-02-01T01:00:00Z");score(manager,cross,home,80);
        var weekly=get(student,RESULTS+"?date=2027-01-01");
        assertThat(weekly.path("period").path("label").asText()).isEqualTo("2026年第53周");
        assertThat(weekly.path("period").path("start").asText()).isEqualTo("2026-12-28");
        assertThat(weekly.path("weeks").get(0).path("submittedAt").asText()).isEqualTo("2027-02-01T01:00:00Z");
        var december=get(student,RESULTS+"?period=MONTH&date=2026-12-01");
        assertThat(december.path("summary").path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(december.path("rankings").path("total").asInt()).isEqualTo(2);
        var january=get(student,RESULTS+"?period=MONTH&date=2027-01-31");
        assertThat(january.path("summary").path("score").isNull()).isTrue();
        assertThat(january.path("summary").path("rank").isNull()).isTrue();
        assertThat(january.path("rankings").path("total").asInt()).isZero();
        assertThat(january.path("weeks").get(0).path("period").path("label").asText()).isEqualTo("2027年第1周");
        assertThat(january.path("trend").get(10).path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(get(student,RESULTS+"?period=MONTH&date=2027-02-01").path("summary").path("samples").asInt()).isZero();
    }

    @Test
    void roundingOccursAfterAverageAndTiesUseRoundedScoresWithStableRoomNumbers() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");var student=login("student");
        long building=building(admin,"模拟小数楼");var home=room(admin,building,"102","student");
        var peer=room(admin,building,"101","peer_student");scheme(admin,"模拟小数标准",0,1,99);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,81,80);score(manager,w1,peer,80,80);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,80,80);
        var half=get(student,RESULTS+"?period=MONTH&date=2026-11-01");
        assertThat(half.path("summary").path("score").decimalValue()).isEqualByComparingTo("80.01");
        assertThat(half.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","2");
        var w3=prepare(manager,"2026-11-16T01:00:00Z");score(manager,w3,home,80,80);
        var rounded=get(student,RESULTS+"?period=MONTH&date=2026-11-01");
        assertThat(rounded.path("summary").path("score").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(rounded.path("summary").path("samples").asInt()).isEqualTo(3);
        assertThat(rounded.path("rankings").path("items").findValuesAsText("roomNo")).containsExactly("101","102");
        assertThat(rounded.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","1");
        assertThat(rounded.path("rankings").path("items").get(0).path("samples").asInt()).isEqualTo(1);
    }

    @Test
    void transferImmediatelyReplacesResultsScopeWhileOwnOldRepairRemainsVisible() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");var student=login("student");
        long a=building(admin,"模拟原楼"),b=building(admin,"模拟新楼");
        var old=room(admin,a,"101","student");var next=room(admin,b,"201","next_student");
        long vacant=post(admin,"/api/housing/beds",Map.of("roomId",next.id,"bedNo","02"),201).path("id").asLong();
        scheme(admin,"模拟调寝标准",0,100);var batch=prepare(manager,"2026-11-16T01:00:00Z");score(manager,batch,old,80);score(manager,batch,next,90);
        var form=new StringBuilder();
        for(var field:Map.of("requestId",UUID.randomUUID().toString(),"category","PLUMBING","description","模拟调寝前报修").entrySet())
            form.append("--fixture\r\nContent-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n").append(field.getValue()).append("\r\n");
        form.append("--fixture--\r\n");
        var repair=student.send("/api/repairs","POST","multipart/form-data; boundary=fixture",form.toString(),student.csrf());
        assertThat(repair.statusCode()).as(repair.body()).isEqualTo(201);long repairId=json.readTree(repair.body()).path("id").asLong();
        assertThat(get(student,RESULTS).path("summary").path("score").decimalValue()).isEqualByComparingTo("80.00");
        var moved=post(admin,"/api/housing/residencies/"+old.residency+"/transfer",Map.of("bedId",vacant,"note","模拟调寝"),200);
        assertThat(student.get(RESULTS+"?roomId="+old.id).statusCode()).isEqualTo(403);
        assertThat(student.get(RESULTS+"?buildingId="+a).statusCode()).isEqualTo(403);
        var current=get(student,RESULTS+"?roomId="+next.id+"&buildingId="+b);
        assertThat(current.path("summary").path("score").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(current.path("trend").get(11).path("score").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(get(student,"/api/repairs/"+repairId).path("roomNo").asText()).isEqualTo("101");
        post(admin,"/api/housing/residencies/"+moved.path("id").asLong()+"/checkout",Map.of("note","模拟退宿"),200);
        assertThat(student.get(RESULTS).statusCode()).isEqualTo(409);
        assertThat(get(student,"/api/repairs/"+repairId).path("roomNo").asText()).isEqualTo("101");
    }

    @Test
    void emptyPeriodsAndInvalidParametersStayExplicitAndCannotBypassIdentity() throws Exception {
        var admin=login("system_admin");var student=login("student");long building=building(admin,"模拟空楼");room(admin,building,"101","student");
        var empty=get(student,RESULTS);
        assertThat(empty.path("summary").path("score").isNull()).isTrue();assertThat(empty.path("summary").path("samples").asInt()).isZero();
        assertThat(empty.path("trend").size()).isEqualTo(12);assertThat(empty.path("weeks").get(0).path("status").asText()).isEqualTo("NOT_PREPARED");
        for(String query:List.of("period=YEAR","date=2026-02-30","date=1900-01-01","page=0","size=101","size=0","roomId=abc"))
            assertThat(student.get(RESULTS+"?"+query).statusCode()).as(query).isEqualTo(400);
        assertThat(student.get(RESULTS+"?roomId=999999").statusCode()).isEqualTo(403);
        assertThat(get(student,RESULTS+"?page=2147483647").path("rankings").path("items").isEmpty()).isTrue();
        long user=admin.account("student").path("id").asLong();post(admin,"/api/identity/accounts/"+user+"/reset-password",Map.of("version",0,"newPassword","ChangedPass2026!"),200);
        assertThat(student.get(RESULTS).statusCode()).isEqualTo(401);
    }

    @Test
    void monthlyAverageRankingAndTrendExcludeMissingDraftAndSuspendedWeeks() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");var student=login("student");
        long building=building(admin,"模拟统计楼");var home=room(admin,building,"103","student");
        var second=room(admin,building,"102","second_student");var first=room(admin,building,"101","first_student");
        var zero=room(admin,building,"104","zero_student");room(admin,building,"105","missing_student");
        scheme(admin,"模拟统计标准",0,100);
        var w1=prepare(manager,"2026-11-02T01:00:00Z");score(manager,w1,home,80);score(manager,w1,first,90);score(manager,w1,second,90);score(manager,w1,zero,0);
        var w2=prepare(manager,"2026-11-09T01:00:00Z");score(manager,w2,home,90);state(admin,w2,"CLOSE");
        var w3=prepare(manager,"2026-11-16T01:00:00Z");score(manager,w3,home,100);state(admin,w3,"SUSPEND");
        var w4=prepare(manager,"2026-11-23T01:00:00Z");
        post(manager,"/api/hygiene/batches/"+w4.path("id").asLong()+"/rooms/"+home.id+"/draft",Map.of("version",0,"dimensions",List.of(Map.of("dimensionId",w4.path("dimensions").get(0).path("id").asLong(),"value","95"))),200);
        var result=get(student,RESULTS+"?period=MONTH&date=2026-11-30&size=2");
        assertThat(result.path("summary").path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(result.path("summary").path("samples").asInt()).isEqualTo(2);
        assertThat(result.path("summary").path("rank").asInt()).isEqualTo(3);
        assertThat(result.path("rankings").path("total").asInt()).isEqualTo(4);
        assertThat(result.path("rankings").path("items").findValuesAsText("roomNo")).containsExactly("101","102");
        assertThat(result.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","1");
        var secondPage=get(student,RESULTS+"?period=MONTH&date=2026-11-30&size=2&page=2").path("rankings").path("items");
        assertThat(secondPage.findValuesAsText("rank")).containsExactly("3","4");
        assertThat(secondPage.get(1).path("score").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(result.path("rankings").toString()).doesNotContain("姓名","student","userId","submittedBy");
        assertThat(result.path("weeks").findValuesAsText("status")).containsExactly("INSPECTED","INSPECTED","SUSPENDED","UNINSPECTED","NOT_PREPARED");
        assertThat(result.path("weeks").get(2).path("totalScore").isNull()).isTrue();
        assertThat(result.path("trend").size()).isEqualTo(12);
        assertThat(result.path("trend").get(11).path("score").decimalValue()).isEqualByComparingTo("85.00");
        assertThat(result.path("trend").get(10).path("score").isNull()).isTrue();
        var weekly=get(student,RESULTS+"?date=2026-11-02");
        assertThat(weekly.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","1","3","4");
        assertThat(weekly.path("summary").path("score").decimalValue()).isEqualByComparingTo("80.00");
        state(admin,w3,"RESTORE");
        var restored=get(student,RESULTS+"?period=MONTH&date=2026-11-30");
        assertThat(restored.path("summary").path("score").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(restored.path("summary").path("samples").asInt()).isEqualTo(3);
        assertThat(restored.path("rankings").path("items").findValuesAsText("rank")).containsExactly("1","1","1","4");
    }

    @Test
    void studentReadsCurrentRoomOriginalStandardAndRejectsForeignScope() throws Exception {
        var admin=login("system_admin");var manager=login("dorm_manager");var student=login("student");
        assertThat(student.get(RESULTS).statusCode()).isEqualTo(409);
        long a=building(admin,"模拟A栋"), b=building(admin,"模拟B栋");
        var home=room(admin,a,"101","student");var other=room(admin,b,"201","other_student");
        var original=scheme(admin,"模拟原始标准",0,40,30,30);
        var batch=prepare(manager,"2026-11-16T01:00:00Z");score(manager,batch,home,80,90,70);
        scheme(admin,"模拟未来标准",original.path("id").asLong(),100);
        var result=get(student,RESULTS+"?date=2026-11-22&roomId="+home.id+"&buildingId="+a);
        assertThat(result.path("scope").path("roomId").asLong()).isEqualTo(home.id);
        assertThat(result.path("period").path("start").asText()).isEqualTo("2026-11-16");
        var week=result.path("weeks").get(0);
        assertThat(week.path("schemeName").asText()).isEqualTo("模拟原始标准");
        assertThat(week.path("threshold").decimalValue()).isEqualByComparingTo("65.00");
        assertThat(week.path("totalScore").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(week.path("dimensions").get(0).path("weight").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(week.path("dimensions").get(1).path("score").asInt()).isEqualTo(90);
        assertThat(student.get(RESULTS+"?roomId="+other.id).statusCode()).isEqualTo(403);
        assertThat(student.get(RESULTS+"?buildingId="+b).statusCode()).isEqualTo(403);
        for(String user:List.of("system_admin","dorm_manager","maintainer")) assertThat(login(user).get(RESULTS).statusCode()).isEqualTo(403);
        assertThat(new Browser().get(RESULTS).statusCode()).isEqualTo(401);
    }
}
