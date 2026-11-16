package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.example.apartment.modules.hygiene.RectificationViews.*;

@Mapper
public interface RectificationMapper {
    String COLUMNS="SELECT id,room_id,building_id,building_name,room_no,status,version,created_at,updated_at,verified_at,closed_at FROM hygiene_rectification";
    String FILTER=" WHERE (#{status} IS NULL OR status=#{status}) AND (#{buildingId} IS NULL OR building_id=#{buildingId})"
            +" AND (#{q}='' OR LOCATE(#{q},room_no)>0 OR LOCATE(#{q},building_name)>0)";
    @Select("SELECT COUNT(*) FROM hygiene_rectification WHERE status='OPEN' AND (#{buildingId} IS NULL OR building_id=#{buildingId}) AND (#{roomId} IS NULL OR room_id=#{roomId})")
    long unfinished(Long buildingId,Long roomId);
    @Select(COLUMNS+" WHERE id=#{id}") Event find(long id);
    @Select(COLUMNS+" WHERE open_room_id=#{roomId}") Event open(long roomId);
    @Select(COLUMNS+FILTER+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}") List<Event> list(Status status,Long buildingId,String q,int size,long offset);
    @Select("SELECT COUNT(*) FROM hygiene_rectification"+FILTER) long count(Status status,Long buildingId,String q);
    @Insert("INSERT INTO hygiene_rectification(room_id,building_id,building_name,room_no,created_at,updated_at) VALUES(#{roomId},#{buildingId},#{buildingName},#{roomNo},#{now},#{now})")
    void create(long roomId,long buildingId,String buildingName,String roomNo,Instant now);
    @Select("SELECT LAST_INSERT_ID()") long insertedId();
    @Update("UPDATE hygiene_rectification SET version=version+1,updated_at=#{now},verified_at=NULL WHERE id=#{id}") void retrigger(long id,Instant now);
    @Insert("INSERT INTO hygiene_rectification_trigger(event_id,score_id,total_score,threshold,triggered_at) VALUES(#{id},#{scoreId},#{score},#{threshold},#{now})")
    void trigger(long id,long scoreId,BigDecimal score,BigDecimal threshold,Instant now);
    @Insert("INSERT INTO hygiene_rectification_log(event_id,action,actor_id,actor_name,note,created_at) VALUES(#{id},#{action},#{actor},#{actorName},#{note},#{now})")
    void log(long id,Action action,long actor,String actorName,String note,Instant now);
    @Update("UPDATE hygiene_rectification SET status=#{status},version=version+1,updated_at=#{now},verified_at=#{verified},closed_at=#{closed} WHERE id=#{id}")
    void update(long id,Status status,Instant now,Instant verified,Instant closed);
    @Select("SELECT t.score_id,s.batch_id,b.iso_year,b.iso_week,b.week_start,b.scheme_id,b.scheme_name,b.status AS batch_status,"
            +"t.total_score,t.threshold,s.total_score AS current_score,s.submitted_at,t.triggered_at FROM hygiene_rectification_trigger t"
            +" JOIN hygiene_score s ON s.id=t.score_id JOIN hygiene_batch b ON b.id=s.batch_id WHERE t.event_id=#{id} ORDER BY t.triggered_at,t.score_id")
    List<Trigger> triggers(long id);
    @Select("SELECT id,action,actor_id,actor_name,note,created_at FROM hygiene_rectification_log WHERE event_id=#{id} ORDER BY id") List<Log> logs(long id);
}
