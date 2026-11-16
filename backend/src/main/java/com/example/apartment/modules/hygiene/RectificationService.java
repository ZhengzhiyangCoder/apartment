package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.*;
import com.example.apartment.support.audit.AuditWriter;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.example.apartment.modules.hygiene.RectificationViews.*;

@Service
public class RectificationService {
    private final RectificationMapper events;
    private final AccountAccess accounts;
    private final AuditWriter audit;
    private final Clock clock;
    public RectificationService(RectificationMapper events,AccountAccess accounts,AuditWriter audit,Clock clock) {
        this.events=events;this.accounts=accounts;this.audit=audit;this.clock=clock;
    }
    /** Formal score caller already holds the shared business lock; failure rolls back the score too. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void trigger(AccountView actor,BatchViews.Room room,BatchViews.ScoreRow score) {
        if(score.totalScore().compareTo(score.threshold())>=0) return;
        var before=events.open(room.roomId());var now=clock.instant();long id;
        if(before==null) {
            events.create(room.roomId(),room.buildingId(),room.buildingName(),room.roomNo(),now);id=events.insertedId();
        } else { id=before.id();events.retrigger(id,now); }
        events.trigger(id,score.id(),score.totalScore(),score.threshold(),now);
        events.log(id,Action.TRIGGERED,actor.id(),actor.displayName(),"正式评分 #"+score.id()+" 低于当时阈值，需跟进核实",now);
        audit.record(actor.id(),"RECTIFICATION_TRIGGERED","RECTIFICATION",id,before,
                java.util.Map.of("event",events.find(id),"scoreId",score.id(),"score",score.totalScore(),"threshold",score.threshold()));
    }
    @Transactional(readOnly=true)
    public Page list(Status status,Long buildingId,String q,int page,int size) {
        if(page<1 || size<1 || size>100 || q.length()>64 || buildingId!=null && buildingId<1)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","关键词最多64字，页码至少为1，每页1–100条，楼栋标识须为正整数");
        return new Page(events.list(status,buildingId,q.strip(),size,(long)(page-1)*size),events.count(status,buildingId,q.strip()),page,size);
    }
    @Transactional(readOnly=true)
    public Detail detail(long id) { return new Detail(require(id),events.triggers(id),events.logs(id)); }
    @Transactional
    public Detail followUp(AccountPrincipal actor,long id,RectificationRequests.Note input) { return change(actor,id,input,Action.FOLLOW_UP); }
    @Transactional
    public Detail verify(AccountPrincipal actor,long id,RectificationRequests.Note input) { return change(actor,id,input,Action.VERIFIED); }
    @Transactional
    public Detail close(AccountPrincipal actor,long id,RectificationRequests.Note input) { return change(actor,id,input,Action.CLOSED); }
    private Detail change(AccountPrincipal actor,long id,RectificationRequests.Note input,Action action) {
        var author=accounts.lockBusinessActor(actor,Role.SYSTEM_ADMIN,Role.DORM_MANAGER);
        var before=require(id);
        if(before.version()!=input.version()) throw new ApiException(HttpStatus.CONFLICT,"RECTIFICATION_CHANGED","整改已被更新，请保留输入并重新载入详情后核对");
        if(before.status()==Status.CLOSED) throw new ApiException(HttpStatus.CONFLICT,"RECTIFICATION_CLOSED","整改事件已关闭，历史记录保留，不能继续修改");
        if(action==Action.CLOSED && before.verifiedAt()==null)
            throw new ApiException(HttpStatus.CONFLICT,"RECTIFICATION_NOT_VERIFIED","请先记录本次核实结果，再填写关闭说明");
        var now=clock.instant();
        events.update(id,action==Action.CLOSED?Status.CLOSED:Status.OPEN,now,
                action==Action.VERIFIED?now:before.verifiedAt(),action==Action.CLOSED?now:null);
        events.log(id,action,author.id(),author.displayName(),input.note().strip(),now);
        var after=events.find(id);
        audit.record(author.id(),"RECTIFICATION_"+action,"RECTIFICATION",id,before,java.util.Map.of("event",after,"note",input.note().strip()));
        return new Detail(after,events.triggers(id),events.logs(id));
    }
    private Event require(long id) {
        var event=events.find(id);
        if(event==null) throw new ApiException(HttpStatus.NOT_FOUND,"RECTIFICATION_NOT_FOUND","整改事件不存在，请刷新列表");
        return event;
    }
}
