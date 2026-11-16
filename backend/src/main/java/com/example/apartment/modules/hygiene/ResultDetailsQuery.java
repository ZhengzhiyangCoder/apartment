package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.hygiene.ResultViews.Period;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.apartment.modules.hygiene.ResultViews.*;

/** Historical standards and effective week details; callers authorize the room before reading. */
@Service
public class ResultDetailsQuery {
    private final BatchMapper batches;
    private final SchemeMapper schemes;
    public ResultDetailsQuery(BatchMapper batches,SchemeMapper schemes) { this.batches=batches;this.schemes=schemes; }
    @Transactional(readOnly=true)
    public List<Week> weeks(long roomId,Period period) {
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
