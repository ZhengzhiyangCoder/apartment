package com.example.apartment.modules.housing;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/** Housing-owned lookup for inspection preparation and management result scope. */
@Service
public class InspectionHousingQuery {
    public record OccupiedRoom(long roomId, long buildingId, String buildingName, String roomNo) {}
    private final ResidencyMapper residencies;
    private final HousingCatalogMapper catalog;
    public InspectionHousingQuery(ResidencyMapper residencies,HousingCatalogMapper catalog) { this.residencies = residencies;this.catalog=catalog; }
    @Transactional(readOnly = true)
    public List<OccupiedRoom> occupiedEnabledRooms() { return residencies.occupiedEnabledRooms(); }
    public record ResultScope(Long buildingId,String buildingName,Long roomId,String roomNo) {}
    @Transactional(readOnly=true)
    public ResultScope resultScope(Long buildingId,Long roomId) {
        if(roomId!=null) {
            var room=catalog.readRoom(roomId);
            if(room==null) throw new ApiException(HttpStatus.NOT_FOUND,"ROOM_NOT_FOUND","寝室不存在");
            if(buildingId!=null && buildingId!=room.buildingId()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","寝室不属于所选楼栋");
            return new ResultScope(room.buildingId(),room.buildingName(),room.id(),room.roomNo());
        }
        if(buildingId==null) return new ResultScope(null,null,null,null);
        var building=catalog.readBuilding(buildingId);
        if(building==null) throw new ApiException(HttpStatus.NOT_FOUND,"BUILDING_NOT_FOUND","楼栋不存在");
        return new ResultScope(building.id(),building.name(),null,null);
    }
}
