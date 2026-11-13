package com.example.apartment.modules.repair;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared business lock must already be held. History remains referenced even after cancellation. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RepairReferences {
    private final RepairMapper repairs;
    public RepairReferences(RepairMapper repairs) { this.repairs = repairs; }
    public void requireAccountFinished(long id) { requireFinished(repairs.openByAccount(id)); }
    public void requireStudentFinished(long id) { requireFinished(repairs.openByStudent(id)); }
    public void requireBuildingFinished(long id) { requireFinished(repairs.openByBuilding(id)); }
    public void requireRoomFinished(long id) { requireFinished(repairs.openByRoom(id)); }
    public void requireBedFinished(long id) { requireFinished(repairs.openByBed(id)); }
    private void requireFinished(long count) {
        if (count > 0) throw new ApiException(HttpStatus.CONFLICT, "UNFINISHED_REPAIRS", "存在未完工单，请先处理相关报修后再停用");
    }
}
