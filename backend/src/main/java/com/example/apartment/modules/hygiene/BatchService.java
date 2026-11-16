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
    private final com.example.apartment.support.audit.BatchChangeQuery changes;
    public BatchService(BatchMapper batches, SchemeMapper schemes, InspectionHousingQuery housing, AccountAccess accounts, AuditWriter audit, Clock clock,
            DraftMapper drafts, com.fasterxml.jackson.databind.ObjectMapper json, com.example.apartment.support.audit.BatchChangeQuery changes) {
        this.batches=batches; this.schemes=schemes; this.housing=housing; this.accounts=accounts; this.audit=audit; this.clock=clock;
        this.drafts=drafts; this.json=json; this.changes=changes;
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
        // Explicit preparation is also the rollover command. Authorized history is never expired here.
        for(var expired:batches.expired(week.weekStart())) {
            batches.state(expired.id(),Status.CLOSED,false,null);
            audit.record(actor.id(),"BATCH_EXPIRED","BATCH",expired.id(),java.util.Map.of("batch",expired),
                    java.util.Map.of("batch",require(expired.id()),"reason","准备本周检查时关闭过期普通录入"));
        }
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
    @Transactional(readOnly=true)
    public Page list(int page,int size) {
        requirePage(page,size);var week=Week.at(clock.instant());
        return new Page(batches.list(size,(long)(page-1)*size).stream().map(b->view(b,week)).toList(),batches.count(),page,size);
    }
    @Transactional
    public Detail state(AccountPrincipal actor, long id, BatchRequests.State input) {
        accounts.lockAdministrator(actor);
        var before=require(id);requireVersion(before,input.version());
        Status status=before.status(), suspendedFrom=null;
        boolean historical=before.historicalReopened();
        var week=Week.at(clock.instant());
        String action;
        switch(input.action()) {
            case CLOSE -> {
                if(status!=Status.OPEN) throw invalidState();
                status=Status.CLOSED;historical=false;action="BATCH_CLOSED";
            }
            case REOPEN -> {
                if(before.weekStart().isAfter(week.weekStart()) || status==Status.SUSPENDED || scoringAllowed(before,week)) throw invalidState();
                status=Status.OPEN;historical=before.weekStart().isBefore(week.weekStart());action="BATCH_REOPENED";
            }
            case SUSPEND -> {
                if(status==Status.SUSPENDED) throw invalidState();
                suspendedFrom=status;status=Status.SUSPENDED;action="BATCH_SUSPENDED";
            }
            case RESTORE -> {
                if(status!=Status.SUSPENDED || before.suspendedFrom()==null) throw invalidState();
                status=before.suspendedFrom();action="BATCH_RESTORED";
                if(status==Status.OPEN && before.weekStart().isBefore(week.weekStart()) && !historical) status=Status.CLOSED;
            }
            default -> throw invalidState();
        }
        batches.state(id,status,historical,suspendedFrom);
        var result=require(id);
        audit.record(actor.id(),action,"BATCH",id,java.util.Map.of("batch",before),
                java.util.Map.of("batch",result,"reason",input.reason().strip()));
        return view(result,Week.at(clock.instant()));
    }
    private void requireVersion(Batch batch,long version) {
        if(batch.version()!=version) throw new ApiException(HttpStatus.CONFLICT,"BATCH_CHANGED","批次已被其他操作修改，请保留原因并核对最新状态");
    }
    private ApiException invalidState() { return new ApiException(HttpStatus.CONFLICT,"INVALID_BATCH_STATE","当前批次状态不支持此操作，请刷新后重新选择"); }
    @Transactional
    public Detail adjustRoom(AccountPrincipal actor,long id,long roomId,BatchRequests.Roster input,boolean add) {
        accounts.lockAdministrator(actor);
        var before=require(id);requireVersion(before,input.version());
        var oldRoom=batches.room(id,roomId);
        if(add) {
            if(oldRoom!=null) throw new ApiException(HttpStatus.CONFLICT,"ROOM_ALREADY_EXPECTED","该寝室已在应检名单中，请刷新名单");
            var room=housing.occupiedEnabledRooms().stream().filter(r->r.roomId()==roomId).findFirst()
                    .orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"ROOM_NOT_ELIGIBLE","仅可补入当前启用且有学生入住的寝室，请核对住宿资料"));
            batches.addRoom(id,roomId,room.buildingId(),room.buildingName(),room.roomNo());
        } else {
            oldRoom=requireRoom(id,roomId);
            if(oldRoom.scoreId()!=null) throw new ApiException(HttpStatus.CONFLICT,"SCORED_ROOM_REQUIRED","已正式评分的寝室不可从应检名单移除");
            batches.removeRoom(id,roomId);
        }
        batches.incrementVersion(id);
        var after=require(id);
        var oldSnapshot=new java.util.HashMap<String,Object>();oldSnapshot.put("batch",before);oldSnapshot.put("room",oldRoom);
        var newSnapshot=new java.util.HashMap<String,Object>();newSnapshot.put("batch",after);newSnapshot.put("room",batches.room(id,roomId));newSnapshot.put("reason",input.reason().strip());
        audit.record(actor.id(),add?"BATCH_ROOM_ADDED":"BATCH_ROOM_REMOVED","BATCH",id,oldSnapshot,newSnapshot);
        return view(after,Week.at(clock.instant()));
    }
    @Transactional(readOnly=true)
    public EligiblePage eligibleRooms(long id,String q,int page,int size) {
        require(id);requirePage(page,size);
        if(q.length()>64) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","关键词最多64字");
        var included=new java.util.HashSet<>(batches.roomIds(id));var keyword=q.strip();
        var candidates=housing.occupiedEnabledRooms().stream().filter(r->!included.contains(r.roomId()))
                .filter(r->r.buildingName().contains(keyword)||r.roomNo().contains(keyword)).toList();
        return new EligiblePage(candidates.stream().skip((long)(page-1)*size).limit(size).toList(),candidates.size(),page,size);
    }
    @Transactional(readOnly=true)
    public com.example.apartment.support.audit.BatchChangeQuery.Page changes(long id,int page,int size) {
        require(id);requirePage(page,size);return changes.list(id,page,size);
    }
    private void requirePage(int page,int size) {
        if(page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","页码至少为1，每页1–100条");
    }
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
        requireScoringAllowed(batch,week);
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
        requireScoringAllowed(batch,Week.at(now));
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
        return new Detail(batch,scoringAllowed(batch,week),rate,schemes.dimensions(batch.schemeId()),batches.buildings(batch.id()));
    }
    private boolean scoringAllowed(Batch batch,Week week) {
        return batch.status()==Status.OPEN && (batch.weekStart().equals(week.weekStart()) || batch.weekStart().isBefore(week.weekStart()) && batch.historicalReopened());
    }
    private void requireScoringAllowed(Batch batch,Week week) {
        if(!scoringAllowed(batch,week)) throw new ApiException(HttpStatus.CONFLICT,"BATCH_NOT_OPEN","批次不可录分或暂存；仅本周开放或系统管理员已授权重开的历史批次可操作，请保留输入并核对状态");
    }
}
