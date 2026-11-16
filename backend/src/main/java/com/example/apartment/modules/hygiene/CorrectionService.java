package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.*;
import com.example.apartment.support.audit.AuditWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionService {
    private final RectificationService rectifications;
    private final CorrectionMapper corrections;
    private final BatchMapper batches;
    private final SchemeMapper schemes;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    private final ObjectMapper json;
    private final Clock clock;
    public CorrectionService(RectificationService rectifications,CorrectionMapper corrections,BatchMapper batches,SchemeMapper schemes,AccountAccess accounts,AuditWriter audit,ObjectMapper json,Clock clock) {
        this.rectifications=rectifications;this.corrections=corrections;this.batches=batches;this.schemes=schemes;this.accounts=accounts;this.audit=audit;this.json=json;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public BatchViews.Score detail(long id) {
        var row=corrections.find(id);
        if(row==null) throw new ApiException(HttpStatus.NOT_FOUND,"SCORE_NOT_FOUND","正式评分不存在");
        return new BatchViews.Score(row,batches.scoreDimensions(id));
    }
    @Transactional(readOnly=true)
    public CorrectionViews.Page history(long id,int page,int size) {
        detail(id);
        if(page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","页码至少为1，每页1–100条");
        var items=corrections.history(id,size,(long)(page-1)*size).stream().map(row->{
            try { return new CorrectionViews.Entry(row.id(),row.scoreId(),row.version(),row.actorId(),row.actorName(),row.reason(),json.readTree(row.beforeData()),json.readTree(row.afterData()),row.createdAt()); }
            catch(JsonProcessingException failure) { throw new IllegalStateException("Cannot decode correction",failure); }
        }).toList();
        return new CorrectionViews.Page(items,corrections.count(id),page,size);
    }
    @Transactional
    public BatchViews.Score correct(AccountPrincipal actor,long id,ScoreRequests.Correct input) {
        var author=accounts.lockBusinessActor(actor,Role.SYSTEM_ADMIN);
        var before=detail(id);var row=before.summary();
        if(row.version()!=input.version()) throw conflict();
        var batch=batches.find(row.batchId());
        var calculated=ScoreCalculation.calculate(schemes.dimensions(batch.schemeId()),input.dimensions());
        if(corrections.update(id,input.version(),calculated.total())!=1) throw conflict();
        calculated.values().forEach((dimension,value)->corrections.dimension(id,dimension,value));
        var after=detail(id);var now=clock.instant();var reason=input.reason().strip();
        try { corrections.record(id,after.summary().version(),author.id(),author.displayName(),reason,json.writeValueAsString(before),json.writeValueAsString(after),now); }
        catch(JsonProcessingException failure) { throw new IllegalStateException("Cannot encode correction",failure); }
        rectifications.corrected(author,batches.room(row.batchId(),row.roomId()),after.summary());
        audit.record(actor.id(),"SCORE_CORRECTED","SCORE",id,before,Map.of("score",after,"reason",reason));
        return after;
    }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT,"SCORE_CHANGED","成绩已被其他管理员更正，输入已保留，请核对最新成绩后再提交"); }
}
