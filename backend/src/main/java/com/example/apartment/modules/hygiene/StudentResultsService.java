package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.housing.CurrentResidencyQuery;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.modules.hygiene.ResultViews.Period;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.hygiene.ResultViews.*;

@Service
public class StudentResultsService {
    private final CurrentResidencyQuery residency;
    private final BatchMapper batches;
    private final SchemeMapper schemes;
    private final ResultMapper results;
    private final Clock clock;
    public StudentResultsService(CurrentResidencyQuery residency,BatchMapper batches,SchemeMapper schemes,ResultMapper results,Clock clock) {
        this.residency=residency;this.batches=batches;this.schemes=schemes;this.results=results;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public Results results(AccountPrincipal actor,Kind kind,LocalDate date,Long roomId,Long buildingId,int page,int size) {
        var current=residency.requireForStudent(actor);
        if((roomId!=null && roomId!=current.roomId()) || (buildingId!=null && buildingId!=current.buildingId()))
            throw new ApiException(HttpStatus.FORBIDDEN,"RESULT_SCOPE_CHANGED","仅可查看当前寝室成绩与当前楼栋排行，请刷新住宿");
        if(date==null) date=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        if(date.getYear()<1901 || date.getYear()>9998) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PERIOD","日期年份须在1901至9998之间");
        if(page<1 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码须为正数，每页1至100项");
        var period=Period.at(kind,date);
        var own=results.own(current.buildingId(),current.roomId(),period.start(),period.end());
        var summary=own==null ? new Sample(null,0,null) : new Sample(own.score(),own.samples(),own.rank());
        long total=results.participants(current.buildingId(),period.start(),period.end());
        var rows=results.rankings(current.buildingId(),period.start(),period.end(),size,((long)page-1)*size).stream()
                .map(r->new Ranking(r.roomNo(),r.score(),r.samples(),r.rank())).toList();
        return new Results(new Scope(current.buildingId(),current.buildingName(),current.roomId(),current.roomNo()),period,summary,
                trend(current.roomId(),period),weeks(current.roomId(),period),new Rankings(rows,total,page,size));
    }
    private List<Trend> trend(long roomId,Period selected) {
        var start=selected.kind()==Kind.WEEK ? selected.start().minusWeeks(11) : selected.start().minusMonths(11);
        var rows=results.trend(roomId,selected.kind().name(),start,selected.end());
        var byStart=new HashMap<LocalDate,ResultMapper.TrendRow>();rows.forEach(r->byStart.put(r.start(),r));
        var trend=new ArrayList<Trend>();
        for(int i=0;i<12;i++) {
            var period=Period.at(selected.kind(),selected.kind()==Kind.WEEK?start.plusWeeks(i):start.plusMonths(i));
            var row=byStart.get(period.start());trend.add(new Trend(period,row==null?null:row.score(),row==null?0:row.samples()));
        }
        return trend;
    }
    private List<Week> weeks(long roomId,Period period) {
        var result=new ArrayList<Week>();
        for(var start=period.start().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));start.isBefore(period.end());start=start.plusWeeks(1)) {
            var week=Period.at(Kind.WEEK,start);
            var batch=batches.current(start.get(IsoFields.WEEK_BASED_YEAR),start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            if(batch==null) { result.add(new Week(week,null,"NOT_PREPARED",null,null,null,null,List.of()));continue; }
            var room=batches.room(batch.id(),roomId);
            var score=room==null ? null : batches.score(batch.id(),roomId);
            String status=batch.status()==BatchViews.Status.SUSPENDED ? "SUSPENDED" : room==null ? "NOT_EXPECTED" : score==null ? "UNINSPECTED" : "INSPECTED";
            // Suspended scores remain stored, but are not effective student results.
            boolean valid=status.equals("INSPECTED");
            List<Dimension> dimensions=valid ? batches.scoreDimensions(score.id()).stream().map(d->new Dimension(d.name(),d.weight(),d.score())).toList()
                    : schemes.dimensions(batch.schemeId()).stream().map(d->new Dimension(d.name(),d.weight(),null)).toList();
            result.add(new Week(week,batch.id(),status,batch.schemeName(),batch.threshold(),valid?score.totalScore():null,valid?score.submittedAt():null,dimensions));
        }
        return result;
    }
}
