package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.example.apartment.modules.hygiene.BatchViews.*;

@Mapper
public interface BatchMapper {
    String COLUMNS = "SELECT b.id, b.iso_year, b.iso_week, b.week_start, b.scheme_id, b.scheme_name, b.threshold, b.status, b.created_by, b.created_at, b.version, b.historical_reopened, b.suspended_from,"
            + " (SELECT COUNT(*) FROM hygiene_batch_room r WHERE r.batch_id = b.id) AS expected_count, (SELECT COUNT(*) FROM hygiene_score s WHERE s.batch_id = b.id) AS inspected_count FROM hygiene_batch b";
    @Select(COLUMNS + " WHERE b.id = #{id}") Batch find(long id);
    @Select(COLUMNS + " WHERE b.iso_year = #{year} AND b.iso_week = #{week}") Batch current(int year, int week);
    @Select(COLUMNS+" ORDER BY b.week_start DESC, b.id DESC LIMIT #{size} OFFSET #{offset}") List<Batch> list(int size,long offset);
    @Select("SELECT COUNT(*) FROM hygiene_batch") long count();
    @Select(COLUMNS+" WHERE b.status='OPEN' AND b.historical_reopened=FALSE AND b.week_start < #{start} ORDER BY b.id")
    List<Batch> expired(LocalDate start);
    @Update("UPDATE hygiene_batch SET status=#{status}, historical_reopened=#{historical}, suspended_from=#{suspendedFrom}, version=version+1 WHERE id=#{id}")
    void state(long id, Status status, boolean historical, Status suspendedFrom);
    @Insert("INSERT INTO hygiene_batch(iso_year, iso_week, week_start, scheme_id, scheme_name, threshold, created_by, created_at)"
            + " VALUES(#{year}, #{week}, #{start}, #{schemeId}, #{schemeName}, #{threshold}, #{actor}, #{now})")
    void create(int year, int week, LocalDate start, long schemeId, String schemeName, BigDecimal threshold, long actor, Instant now);
    @Select("SELECT LAST_INSERT_ID()") long insertedId();
    @Insert("INSERT INTO hygiene_batch_room(batch_id, room_id, building_id, building_name, room_no) VALUES(#{batchId}, #{roomId}, #{buildingId}, #{buildingName}, #{roomNo})")
    void addRoom(long batchId, long roomId, long buildingId, String buildingName, String roomNo);
    @Delete("DELETE FROM hygiene_batch_room WHERE batch_id=#{id} AND room_id=#{roomId}") void removeRoom(long id,long roomId);
    @Select("SELECT room_id FROM hygiene_batch_room WHERE batch_id=#{id}") List<Long> roomIds(long id);
    @Update("UPDATE hygiene_batch SET version=version+1 WHERE id=#{id}") void incrementVersion(long id);
    String ROOM_FROM = " FROM hygiene_batch_room r LEFT JOIN hygiene_score s ON s.batch_id = r.batch_id AND s.room_id = r.room_id";
    String ROOM_COLUMNS = "SELECT r.room_id, r.building_id, r.building_name, r.room_no, s.id AS score_id, s.total_score" + ROOM_FROM;
    String FILTER = " WHERE r.batch_id = #{id} AND (#{buildingId} IS NULL OR r.building_id = #{buildingId})"
            + " AND (#{inspected} IS NULL OR (s.id IS NOT NULL) = #{inspected}) AND (#{q} = '' OR LOCATE(#{q}, r.room_no) > 0 OR LOCATE(#{q}, r.building_name) > 0)";
    @Select(ROOM_COLUMNS + FILTER + " ORDER BY r.building_name, r.room_no, r.room_id LIMIT #{size} OFFSET #{offset}")
    List<Room> rooms(long id, Long buildingId, String q, Boolean inspected, int size, long offset);
    @Select("SELECT COUNT(*)" + ROOM_FROM + FILTER)
    long countRooms(long id, Long buildingId, String q, Boolean inspected);
    @Select("SELECT DISTINCT building_id AS id, building_name AS name FROM hygiene_batch_room WHERE batch_id = #{id} ORDER BY building_name, building_id")
    List<Building> buildings(long id);
    @Select(ROOM_COLUMNS + " WHERE r.batch_id = #{id} AND r.room_id = #{roomId}") Room room(long id, long roomId);
    @Select("SELECT id, batch_id, room_id, total_score, threshold, submitted_by, submitted_at, version FROM hygiene_score WHERE batch_id = #{id} AND room_id = #{roomId}")
    ScoreRow score(long id, long roomId);
    @Select("SELECT dimension_id, name, weight, score, sort_order FROM hygiene_score_dimension WHERE score_id = #{id} ORDER BY sort_order")
    List<ScoredDimension> scoreDimensions(long id);
    @Insert("INSERT INTO hygiene_score(batch_id, room_id, total_score, threshold, submitted_by, submitted_at) VALUES(#{id}, #{roomId}, #{total}, #{threshold}, #{actor}, #{now})")
    void insertScore(long id, long roomId, BigDecimal total, BigDecimal threshold, long actor, Instant now);
    @Insert("INSERT INTO hygiene_score_dimension(score_id, dimension_id, name, weight, score, sort_order) VALUES(#{id}, #{dimensionId}, #{name}, #{weight}, #{score}, #{position})")
    void insertScoreDimension(long id, long dimensionId, String name, BigDecimal weight, int score, int position);

    @Select("SELECT COUNT(*)" + ROOM_FROM + " JOIN hygiene_batch b ON b.id = r.batch_id WHERE b.status = 'OPEN' AND s.id IS NULL"
            + " AND (b.week_start = #{start} OR (b.week_start < #{start} AND b.historical_reopened = TRUE))"
            + " AND (#{buildingId} IS NULL OR r.building_id = #{buildingId}) AND (#{roomId} IS NULL OR r.room_id = #{roomId})")
    long unfinished(Long buildingId, Long roomId,LocalDate start);

}
