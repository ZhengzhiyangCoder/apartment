package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Inspection belongs to a room, not its former occupants or individual beds. Caller holds the business lock. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class InspectionReferences {
    private final BatchMapper batches;
    private final java.time.Clock clock;
    public InspectionReferences(BatchMapper batches,java.time.Clock clock) { this.batches=batches;this.clock=clock; }
    public void requireBuildingFinished(long id) { requireFinished(batches.unfinished(id,null,BatchViews.Week.at(clock.instant()).weekStart())); }
    public void requireRoomFinished(long id) { requireFinished(batches.unfinished(null,id,BatchViews.Week.at(clock.instant()).weekStart())); }
    private void requireFinished(long count) {
        if(count>0) throw new ApiException(HttpStatus.CONFLICT,"UNFINISHED_INSPECTION","存在开放批次的未检查寝室，请先处理应检事项后再停用");
    }
}
