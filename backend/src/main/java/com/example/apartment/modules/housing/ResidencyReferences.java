package com.example.apartment.modules.housing;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Call only after the shared administrator lock: deactivation and check-in share one transaction order. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ResidencyReferences {
    private final ResidencyMapper residencies;
    private final com.example.apartment.modules.repair.RepairReferences repairs;
    private final com.example.apartment.modules.hygiene.InspectionReferences inspections;
    private final com.example.apartment.modules.hygiene.RectificationReferences rectifications;
    public ResidencyReferences(ResidencyMapper residencies, com.example.apartment.modules.repair.RepairReferences repairs,
            com.example.apartment.modules.hygiene.InspectionReferences inspections, com.example.apartment.modules.hygiene.RectificationReferences rectifications) {
        this.residencies = residencies; this.repairs = repairs; this.inspections = inspections; this.rectifications = rectifications;
    }
    public void requireAccountVacant(long id) { requireVacant(residencies.currentByAccount(id)); repairs.requireAccountFinished(id); }
    public void requireStudentVacant(long id) { requireVacant(residencies.currentByStudent(id)); repairs.requireStudentFinished(id); }
    public void requireBuildingVacant(long id) { requireVacant(residencies.currentByBuilding(id)); repairs.requireBuildingFinished(id); inspections.requireBuildingFinished(id); rectifications.requireBuildingFinished(id); }
    public void requireRoomVacant(long id) { requireVacant(residencies.currentByRoom(id)); repairs.requireRoomFinished(id); inspections.requireRoomFinished(id); rectifications.requireRoomFinished(id); }
    public void requireBedVacant(long id) { requireVacant(residencies.currentByBed(id)); repairs.requireBedFinished(id); }
    private void requireVacant(long count) {
        if (count > 0) throw new ApiException(HttpStatus.CONFLICT, "CURRENT_RESIDENCY_EXISTS", "存在当前入住，请先办理调寝或退宿后再停用");
    }
}
