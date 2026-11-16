package com.example.apartment.support.audit;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQuery {
    public enum Target { ACCOUNT, BUILDING, ROOM, BED, STUDENT, RESIDENCY, REPAIR, SCHEME, BATCH, SCORE, RECTIFICATION }
    public enum Action {
        ACCOUNT_CREATED, ACCOUNT_ROLE_CHANGED, ACCOUNT_ENABLED, ACCOUNT_DISABLED, ACCOUNT_PASSWORD_RESET,
        BUILDING_CREATED, BUILDING_UPDATED, BUILDING_ENABLED, BUILDING_DISABLED,
        ROOM_CREATED, ROOM_UPDATED, ROOM_ENABLED, ROOM_DISABLED, BED_CREATED, BED_UPDATED, BED_ENABLED, BED_DISABLED,
        STUDENT_CREATED, STUDENT_UPDATED, STUDENT_ENABLED, STUDENT_DISABLED,
        RESIDENCY_CHECKED_IN, RESIDENCY_TRANSFERRED, RESIDENCY_CHECKED_OUT,
        REPAIR_SUBMITTED, REPAIR_ASSIGNED, REPAIR_REASSIGNED, REPAIR_STARTED, REPAIR_COMPLETED, REPAIR_CANCELLED,
        SCHEME_CREATED, SCHEME_UPDATED, SCHEME_COPIED, SCHEME_ARCHIVED, SCHEME_ACTIVATED,
        BATCH_EXPIRED, BATCH_PREPARED, BATCH_CLOSED, BATCH_REOPENED, BATCH_SUSPENDED, BATCH_RESTORED, BATCH_ROOM_ADDED, BATCH_ROOM_REMOVED,
        SCORE_SUBMITTED, SCORE_DRAFT_SAVED, SCORE_CORRECTED,
        RECTIFICATION_TRIGGERED, RECTIFICATION_CORRECTED, RECTIFICATION_FOLLOW_UP, RECTIFICATION_VERIFIED, RECTIFICATION_CLOSED
    }
    public enum Sort { NEWEST, OLDEST }
    public record Filter(Long actorId,Target targetType,Long targetId,Action action,Instant from,Instant until,Sort sort,int page,int size) {
        public long offset() { return (long)(page-1)*size; }
    }
    public record Row(long id,long actorId,String action,String targetType,long targetId,Instant createdAt,String beforeData,String afterData) {}
    public record Entry(long id,long actorId,String actorName,String action,String targetType,long targetId,Instant createdAt) {}
    public record Page(List<Entry> items,long total,int page,int size,String scope) {}
    public record Reference(String label,String path) {}
    public record Detail(Entry entry,JsonNode before,JsonNode after,String reason,List<Reference> references) {}
    private final AuditLogMapper logs;
    private final AccountAccess accounts;
    private final ObjectMapper json;
    public AuditQuery(AuditLogMapper logs,AccountAccess accounts,ObjectMapper json) { this.logs=logs;this.accounts=accounts;this.json=json; }
    private Long owner(AccountPrincipal actor) {
        if(actor.role()==Role.SYSTEM_ADMIN) return null;
        if(actor.role()==Role.DORM_MANAGER) return actor.id();
        throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权查询管理审计");
    }
    @Transactional(readOnly=true)
    public Page list(AccountPrincipal actor,Filter input) {
        Long own=owner(actor);
        if(input.page()<1 || input.size()<1 || input.size()>100 || input.actorId()!=null && input.actorId()<1 || input.targetId()!=null && input.targetId()<1
                || input.from()!=null && input.until()!=null && !input.from().isBefore(input.until())) throw invalid();
        if(own!=null && input.actorId()!=null && !own.equals(input.actorId())) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","仅可查询本人操作记录");
        var filter=new Filter(own==null?input.actorId():own,input.targetType(),input.targetId(),input.action(),input.from(),input.until(),input.sort(),input.page(),input.size());
        var rows=logs.search(filter);var names=accounts.actorNames(rows.stream().map(Row::actorId).distinct().toList());
        return new Page(rows.stream().map(row->entry(row,names.get(row.actorId()))).toList(),logs.countSearch(filter),filter.page(),filter.size(),own==null?"ALL":"OWN");
    }
    @Transactional(readOnly=true)
    public Detail detail(AccountPrincipal actor,long id) {
        var own=owner(actor);var row=logs.auditRecord(id,own);
        if(row==null) throw new ApiException(HttpStatus.NOT_FOUND,"AUDIT_NOT_FOUND","记录不存在或无访问权限");
        var before=decode(row.beforeData());var after=decode(row.afterData());
        return new Detail(entry(row,accounts.actorNames(List.of(row.actorId())).get(row.actorId())),
                project(row.targetType(),before),project(row.targetType(),after),reason(row.targetType(),after),references(row,before,after));
    }
    private Entry entry(Row row,String name) { return new Entry(row.id(),row.actorId(),name,row.action(),row.targetType(),row.targetId(),row.createdAt()); }
    private JsonNode decode(String data) {
        if(data==null) return NullNode.instance;
        try { return json.readTree(data); } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("审计快照无法读取"); }
    }
    // Explicit shape selection: new domain fields are not exposed by default.
    private JsonNode project(String type,JsonNode data) {
        if(data.isNull() || data.isMissingNode()) return NullNode.instance;
        return switch(type) {
            case "ACCOUNT" -> pick(data,"id username displayName role enabled");
            case "BUILDING" -> pick(data,"id name enabled");
            case "ROOM" -> pick(data,"id buildingId buildingName roomNo floor enabled");
            case "BED" -> pick(data,"id roomId buildingId buildingName roomNo bedNo enabled");
            case "STUDENT" -> pick(data,"id userId studentNo fullName college major className enabled");
            case "RESIDENCY" -> {
                if(data.has("current")) {
                    var result=json.createObjectNode();result.set("previous",project(type,data.path("previous")));result.set("current",project(type,data.path("current")));yield result;
                }
                yield pick(data,"id studentId buildingId buildingName roomId roomNo bedId bedNo startedAt endedAt endReason endNote");
            }
            case "REPAIR" -> pick(data,"id buildingId buildingName roomId roomNo category status assigneeId assigneeName assignedAt startedAt completedAt completionNote version");
            case "SCHEME" -> {
                var result=pick(data,"id name threshold status version sourceId activatedAt");
                result.set("dimensions",array(data.path("dimensions"),"id name weight sortOrder"));yield result;
            }
            case "BATCH" -> {
                var result=pick(data,"roomId version");
                if(data.has("batch")) result.set("batch",pick(data.path("batch"),"id isoYear isoWeek weekStart schemeId schemeName threshold status version historicalReopened suspendedFrom expectedCount inspectedCount"));
                if(data.has("room")) result.set("room",data.path("room").isNull()?NullNode.instance:pick(data.path("room"),"roomId buildingId buildingName roomNo scoreId totalScore"));
                // Initial roster stays in the batch domain; its count and immutable batch id suffice here.
                yield result;
            }
            case "SCORE" -> {
                var score=data.has("score")?data.path("score"):data;
                var result=pick(score,"id batchId roomId totalScore threshold submittedBy submittedAt version");
                result.set("dimensions",array(score.path("dimensions"),"dimensionId name weight score sortOrder"));yield result;
            }
            case "RECTIFICATION" -> {
                var event=data.has("event")?data.path("event"):data;
                var result=pick(event,"id roomId buildingId buildingName roomNo status version createdAt updatedAt verifiedAt closedAt");
                result.setAll(pick(data,"scoreId score threshold"));yield result;
            }
            default -> json.createObjectNode();
        };
    }
    private ArrayNode array(JsonNode data,String fields) {
        var result=json.createArrayNode();if(data.isArray()) for(var item:data) result.add(pick(item,fields));return result;
    }
    private String reason(String type,JsonNode after) {
        return switch(type) {
            case "REPAIR" -> { var logs=after.path("logs");yield logs.isArray() && !logs.isEmpty()?logs.get(logs.size()-1).path("note").asText(""):""; }
            case "RESIDENCY" -> (after.has("previous")?after.path("previous"):after).path("endNote").asText("");
            case "BATCH","SCORE" -> after.path("reason").asText("");
            case "RECTIFICATION" -> after.path("note").asText("");
            default -> "";
        };
    }
    private List<Reference> references(Row row,JsonNode before,JsonNode after) {
        long id=row.targetId();
        return switch(row.targetType()) {
            case "ACCOUNT" -> List.of(new Reference("账号资料","/accounts?record="+id));
            case "BUILDING","ROOM","BED","STUDENT" -> {
                String kind=switch(row.targetType()) { case "BUILDING" -> "buildings";case "ROOM" -> "rooms";case "BED" -> "beds";default -> "students"; };
                yield List.of(new Reference("住宿资料","/housing?kind="+kind+"&record="+id));
            }
            case "RESIDENCY" -> {
                var refs=new ArrayList<Reference>();refs.add(new Reference("原住宿记录","/residencies?record="+id));
                if(after.path("current").path("id").asLong()>0) refs.add(new Reference("调寝后的住宿记录","/residencies?record="+after.path("current").path("id").asLong()));yield refs;
            }
            case "REPAIR" -> List.of(new Reference("工单详情与状态日志","/repairs?record="+id));
            case "SCHEME" -> List.of(new Reference("评分方案版本","/schemes?record="+id));
            case "BATCH" -> List.of(new Reference("批次与名单变更记录","/inspections?batch="+id));
            case "SCORE" -> {
                var score=after.has("score")?after.path("score"):after;
                yield List.of(new Reference("正式成绩与更正明细","/inspections?batch="+score.path("batchId").asLong()+"&room="+score.path("roomId").asLong()));
            }
            case "RECTIFICATION" -> List.of(new Reference("整改依据与处理记录","/rectifications?record="+id));
            default -> List.of();
        };
    }
    private ObjectNode pick(JsonNode source,String fields) {
        var result=json.createObjectNode();
        for(String key:fields.split(" ")) if(source.has(key) && source.get(key).isValueNode()) result.set(key,source.get(key));
        return result;
    }
    static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","请检查筛选范围、排序和分页参数"); }
}
