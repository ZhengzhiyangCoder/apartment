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
    public ResidencyReferences(ResidencyMapper residencies) { this.residencies = residencies; }
    public void requireAccountVacant(long id) { requireVacant(residencies.currentByAccount(id)); }
    public void requireStudentVacant(long id) { requireVacant(residencies.currentByStudent(id)); }
    public void requireBuildingVacant(long id) { requireVacant(residencies.currentByBuilding(id)); }
    public void requireRoomVacant(long id) { requireVacant(residencies.currentByRoom(id)); }
    public void requireBedVacant(long id) { requireVacant(residencies.currentByBed(id)); }
    private void requireVacant(long count) {
        if (count > 0) throw new ApiException(HttpStatus.CONFLICT, "CURRENT_RESIDENCY_EXISTS", "存在当前入住，请先办理调寝或退宿后再停用");
    }
}
