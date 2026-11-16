package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.housing.InspectionHousingQuery;
import com.example.apartment.modules.identity.*;
import com.example.apartment.support.audit.AuditWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.hygiene.BatchViews.*;

@Service
public class BatchService {
    private final BatchMapper batches;
    private final SchemeMapper schemes;
    private final InspectionHousingQuery housing;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    private final Clock clock;
    private final DraftMapper drafts;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public BatchService(BatchMapper batches, SchemeMapper schemes, InspectionHousingQuery housing, AccountAccess accounts, AuditWriter audit, Clock clock,
            DraftMapper drafts, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.batches=batches; this.schemes=schemes; this.housing=housing; this.accounts=accounts; this.audit=audit; this.clock=clock;
        this.drafts=drafts; this.json=json;
    }
    @Transactional(readOnly = true)
    public Current current() {
        var week=Week.at(clock.instant()); var batch=batches.current(week.isoYear(),week.isoWeek());
        return new Current(week,batch==null?null:view(batch,week));
    }
    @Transactional
    public Detail prepare(AccountPrincipal actor) {
        accounts.lockBusinessActor(actor, Role.SYSTEM_ADMIN, Role.DORM_MANAGER);
        var now=clock.instant();var week=Week.at(now);
        var existing=batches.current(week.isoYear(),week.isoWeek());
        if(existing!=null) return view(existing,week);
        var scheme=schemes.active();
        if(scheme==null) throw new ApiException(HttpStatus.CONFLICT,"NO_ACTIVE_SCHEME","尚无启用评分方案，请联系系统管理员先启用方案");
        var rooms=housing.occupiedEnabledRooms();
        batches.create(week.isoYear(),week.isoWeek(),week.weekStart(),scheme.id(),scheme.name(),scheme.threshold(),actor.id(),now);
        long id=batches.insertedId();
        for(var room:rooms) batches.addRoom(id,room.roomId(),room.buildingId(),room.buildingName(),room.roomNo());
        var result=view(batches.find(id),week);
        audit.record(actor.id(),"BATCH_PREPARED","BATCH",id,null,java.util.Map.of("batch",result,"rooms",rooms));
        return result;
    }
    @Transactional(readOnly = true)
    public Detail detail(long id) { return view(require(id),Week.at(clock.instant())); }
    @Transactional(readOnly = true)
    public RoomPage rooms(long id, Long buildingId, String q, Boolean inspected, int page, int size) {
        require(id);
        if(q.length()>64 || page<1 || size<1 || size>100 || buildingId!=null && buildingId<1)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","关键词最多64字，页码至少为1，每页1–100条，楼栋标识须为正整数");
        return new RoomPage(batches.rooms(id,buildingId,q.strip(),inspected,size,(long)(page-1)*size),batches.countRooms(id,buildingId,q.strip(),inspected),page,size);
    }
    @Transactional(readOnly = true)
    public RoomDetail room(long id, long roomId) {
        require(id);var room=requireRoom(id,roomId);
        return new RoomDetail(room,score(id,roomId));
    }
    @Transactional
    public Score submit(AccountPrincipal actor, long id, long roomId, ScoreRequests.Submit input) {
        accounts.lockBusinessActor(actor,Role.DORM_MANAGER);
        var now=clock.instant();var batch=require(id);var week=Week.at(now);
        if(batch.status()!=Status.OPEN || !batch.weekStart().equals(week.weekStart()))
            throw new ApiException(HttpStatus.CONFLICT,"BATCH_NOT_OPEN","仅当前周开放批次允许普通录分，请刷新批次信息");
        var room=requireRoom(id,roomId);
        if(room.scoreId()!=null) throw alreadyScored();
        var dimensions=schemes.dimensions(batch.schemeId());
        var draft=drafts.find(actor.id(),id,roomId);
        if(input.draftVersion()!=null && input.draftVersion()!=(draft==null?0:draft.version())) throw draftConflict();
        var supplied=new java.util.HashMap<Long,Integer>();
        for(var dimension:input.dimensions()) {
            if(supplied.put(dimension.dimensionId(),dimension.score())!=null) throw invalidDimensions();
        }
        if(supplied.size()!=dimensions.size() || dimensions.stream().anyMatch(d->!supplied.containsKey(d.id()))) throw invalidDimensions();
        var total=BigDecimal.ZERO;
        for(var dimension:dimensions) total=total.add(dimension.weight().multiply(BigDecimal.valueOf(supplied.get(dimension.id()))));
        total=total.divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
        try { batches.insertScore(id,roomId,total,batch.threshold(),actor.id(),now); }
        catch(org.springframework.dao.DuplicateKeyException failure) { throw alreadyScored(); }
        long scoreId=batches.insertedId();
        for(var dimension:dimensions) batches.insertScoreDimension(scoreId,dimension.id(),dimension.name(),dimension.weight(),supplied.get(dimension.id()),dimension.sortOrder());
        var result=score(id,roomId);
        drafts.clear(actor.id(),id,roomId);
        audit.record(actor.id(),"SCORE_SUBMITTED","SCORE",scoreId,null,result);
        return result;
    }
    private Score score(long id, long roomId) {
        var result=batches.score(id,roomId);
        return result==null?null:new Score(result,batches.scoreDimensions(result.id()));
    }
    @Transactional(readOnly = true)
    public DraftDtos.Page drafts(AccountPrincipal actor, int page, int size) {
        if(page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","页码至少为1，每页1–100条");
        return new DraftDtos.Page(drafts.list(actor.id(),size,(long)(page-1)*size).stream().map(this::draftView).toList(),drafts.count(actor.id()),page,size);
    }
    @Transactional(readOnly = true)
    public DraftDtos.Draft draft(AccountPrincipal actor, long id, long roomId) {
        require(id);
        return draftView(drafts.find(actor.id(),id,roomId));
    }
    @Transactional
    public DraftDtos.Draft saveDraft(AccountPrincipal actor, long id, long roomId, DraftDtos.Save input) {
        accounts.lockBusinessActor(actor,Role.DORM_MANAGER);
        var batch=require(id);var now=clock.instant();
        if(batch.status()!=Status.OPEN || !batch.weekStart().equals(Week.at(now).weekStart()))
            throw new ApiException(HttpStatus.CONFLICT,"BATCH_NOT_OPEN","仅当前周开放批次允许暂存，请保留输入并核对批次");
        var room=requireRoom(id,roomId);
        if(room.scoreId()!=null) throw alreadyScored();
        var before=drafts.find(actor.id(),id,roomId);
        if(input.version()!=(before==null?0:before.version())) throw draftConflict();
        var allowed=schemes.dimensions(batch.schemeId()).stream().map(SchemeViews.Dimension::id).collect(java.util.stream.Collectors.toSet());
        var seen=new java.util.HashSet<Long>();
        for(var value:input.dimensions()) if(!allowed.contains(value.dimensionId()) || !seen.add(value.dimensionId()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DIMENSIONS","草稿仅可包含本批次方案的维度，不能重复");
        try { drafts.save(actor.id(),id,roomId,room.buildingName(),room.roomNo(),json.writeValueAsString(input.dimensions()),now); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Cannot encode draft",failure); }
        var saved=draftView(drafts.find(actor.id(),id,roomId));
        // Audit identifies the change without exposing private draft input to future audit viewers.
        audit.record(actor.id(),"SCORE_DRAFT_SAVED","BATCH",id,
                before==null?null:java.util.Map.of("roomId",roomId,"version",before.version()),
                java.util.Map.of("roomId",roomId,"version",saved.version()));
        return saved;
    }
    private DraftDtos.Draft draftView(DraftDtos.Row row) {
        if(row==null) return null;
        try {
            var values=json.readValue(row.dimensions(),new com.fasterxml.jackson.core.type.TypeReference<java.util.List<DraftDtos.Value>>() {});
            return new DraftDtos.Draft(row.batchId(),row.roomId(),row.buildingName(),row.roomNo(),row.isoYear(),row.isoWeek(),row.status(),row.version(),row.updatedAt(),values);
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Cannot decode draft",failure); }
    }
    private ApiException draftConflict() { return new ApiException(HttpStatus.CONFLICT,"DRAFT_CHANGED","个人草稿已在另一窗口更新，请保留输入并核对最新草稿"); }
    private Room requireRoom(long id, long roomId) {
        var room=batches.room(id,roomId);
        if(room==null) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_EXPECTED_ROOM","该寝室不在本批次应检名单中");
        return room;
    }
    private ApiException alreadyScored() { return new ApiException(HttpStatus.CONFLICT,"ALREADY_SCORED","该寝室已提交正式评分，请查看已保存结果，不可重复录分"); }
    private ApiException invalidDimensions() { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DIMENSIONS","必须完整提交本批次方案的全部维度，不能重复或添加其他维度"); }
    private Batch require(long id) {
        var batch=batches.find(id);
        if(batch==null) throw new ApiException(HttpStatus.NOT_FOUND,"BATCH_NOT_FOUND","检查批次不存在，请刷新页面");
        return batch;
    }
    private Detail view(Batch batch, Week week) {
        var rate=batch.expectedCount()==0?null:BigDecimal.valueOf(batch.inspectedCount()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(batch.expectedCount()),2,RoundingMode.HALF_UP);
        return new Detail(batch,batch.status()==Status.OPEN && batch.weekStart().equals(week.weekStart()),rate,schemes.dimensions(batch.schemeId()),batches.buildings(batch.id()));
    }
}
