package com.example.apartment.modules.hygiene;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DraftMapper {
    String COLUMNS = "SELECT d.batch_id, d.room_id, d.building_name, d.room_no, b.iso_year, b.iso_week, b.status, d.version, d.updated_at, d.dimensions"
            + " FROM hygiene_score_draft d JOIN hygiene_batch b ON b.id=d.batch_id WHERE d.actor_id=#{actor}";
    @Select(COLUMNS + " AND d.batch_id=#{batch} AND d.room_id=#{room}") DraftDtos.Row find(long actor, long batch, long room);
    @Select(COLUMNS + " ORDER BY d.updated_at DESC, d.batch_id DESC, d.room_id LIMIT #{size} OFFSET #{offset}")
    List<DraftDtos.Row> list(long actor, int size, long offset);
    @Select("SELECT COUNT(*) FROM hygiene_score_draft WHERE actor_id=#{actor}") long count(long actor);
    @Insert("INSERT INTO hygiene_score_draft(actor_id,batch_id,room_id,building_name,room_no,dimensions,version,updated_at)"
            + " VALUES(#{actor},#{batch},#{room},#{buildingName},#{roomNo},#{dimensions},1,#{now})"
            + " ON DUPLICATE KEY UPDATE dimensions=#{dimensions},version=version+1,updated_at=#{now}")
    void save(long actor, long batch, long room, String buildingName, String roomNo, String dimensions, Instant now);
    @Delete("DELETE FROM hygiene_score_draft WHERE actor_id=#{actor} AND batch_id=#{batch} AND room_id=#{room}")
    void clear(long actor, long batch, long room);
}
