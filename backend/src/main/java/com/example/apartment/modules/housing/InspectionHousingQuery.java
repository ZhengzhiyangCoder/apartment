package com.example.apartment.modules.housing;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called after the shared business lock; check-in/out and deactivation use that same lock. */
@Service
public class InspectionHousingQuery {
    public record OccupiedRoom(long roomId, long buildingId, String buildingName, String roomNo) {}
    private final ResidencyMapper residencies;
    public InspectionHousingQuery(ResidencyMapper residencies) { this.residencies = residencies; }
    @Transactional(readOnly = true)
    public List<OccupiedRoom> occupiedEnabledRooms() { return residencies.occupiedEnabledRooms(); }
}
