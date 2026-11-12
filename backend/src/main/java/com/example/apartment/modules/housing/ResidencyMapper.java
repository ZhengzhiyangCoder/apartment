package com.example.apartment.modules.housing;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ResidencyMapper {
    String FROM = " FROM housing_residency h JOIN student_profile s ON s.id = h.student_id"
            + " JOIN dorm_bed d ON d.id = h.bed_id JOIN dorm_room r ON r.id = d.room_id JOIN dorm_building b ON b.id = r.building_id";
    String COLUMNS = "SELECT h.id, h.student_id, s.student_no, s.full_name, b.id AS building_id,"
            + " IF(h.ended_at IS NULL, b.name, h.building_name) AS building_name, r.id AS room_id,"
            + " IF(h.ended_at IS NULL, r.room_no, h.room_no) AS room_no, d.id AS bed_id,"
            + " IF(h.ended_at IS NULL, d.bed_no, h.bed_no) AS bed_no, h.started_at, h.ended_at, h.end_reason, h.end_note";
    String FILTER = " WHERE (#{studentId} IS NULL OR h.student_id = #{studentId})"
            + " AND (#{current} IS NULL OR (h.ended_at IS NULL) = #{current})"
            + " AND (#{q} = '' OR LOCATE(#{q}, s.student_no) > 0 OR LOCATE(#{q}, s.full_name) > 0"
            + " OR LOCATE(#{q}, IF(h.ended_at IS NULL, b.name, h.building_name)) > 0"
            + " OR LOCATE(#{q}, IF(h.ended_at IS NULL, r.room_no, h.room_no)) > 0)";
    @Select(COLUMNS + FROM + FILTER + " ORDER BY h.id DESC LIMIT #{size} OFFSET #{offset}")
    List<ResidencyView> list(String q, Long studentId, Boolean current, int size, long offset);
    @Select("SELECT COUNT(*)" + FROM + FILTER)
    long count(String q, Long studentId, Boolean current);
    @Select(COLUMNS + FROM + " WHERE h.id = #{id}")
    ResidencyView find(long id);
    @Select(COLUMNS + FROM + " WHERE s.user_id = #{userId} AND h.ended_at IS NULL")
    ResidencyView currentForUser(long userId);
    @Select("SELECT COUNT(*) FROM housing_residency WHERE active_student_id = #{studentId} OR active_bed_id = #{bedId}")
    long occupied(long studentId, long bedId);
    @Insert("INSERT INTO housing_residency(student_id, bed_id, building_name, room_no, bed_no, started_by)"
            + " VALUES (#{studentId}, #{bedId}, #{buildingName}, #{roomNo}, #{bedNo}, #{actorId})")
    void create(long studentId, long bedId, String buildingName, String roomNo, String bedNo, long actorId);
    @Update("UPDATE housing_residency SET ended_at = CURRENT_TIMESTAMP(6), ended_by = #{actorId}, end_reason = #{reason}, end_note = #{note}"
            + " WHERE id = #{id} AND ended_at IS NULL")
    int end(long id, long actorId, String reason, String note);
    @Select("SELECT COUNT(*)" + FROM + " WHERE h.ended_at IS NULL AND s.user_id = #{userId}")
    long currentByAccount(long userId);
    @Select("SELECT COUNT(*) FROM housing_residency WHERE active_student_id = #{studentId}")
    long currentByStudent(long studentId);
    @Select("SELECT COUNT(*) FROM housing_residency WHERE active_bed_id = #{bedId}")
    long currentByBed(long bedId);
    @Select("SELECT COUNT(*)" + FROM + " WHERE h.ended_at IS NULL AND r.id = #{roomId}")
    long currentByRoom(long roomId);
    @Select("SELECT COUNT(*)" + FROM + " WHERE h.ended_at IS NULL AND b.id = #{buildingId}")
    long currentByBuilding(long buildingId);
    String VACANCY_FILTER = " WHERE d.enabled = TRUE AND r.enabled = TRUE AND b.enabled = TRUE"
            + " AND NOT EXISTS (SELECT 1 FROM housing_residency h WHERE h.active_bed_id = d.id)"
            + " AND (#{q} = '' OR LOCATE(#{q}, b.name) > 0 OR LOCATE(#{q}, r.room_no) > 0 OR LOCATE(#{q}, d.bed_no) > 0)";
    @Select(HousingCatalogMapper.BED_COLUMNS + HousingCatalogMapper.BED_FROM + VACANCY_FILTER + " ORDER BY d.id DESC LIMIT #{size} OFFSET #{offset}")
    List<HousingViews.Bed> vacantBeds(String q, int size, long offset);
    @Select("SELECT COUNT(*)" + HousingCatalogMapper.BED_FROM + VACANCY_FILTER)
    long countVacantBeds(String q);
    @Select("SELECT LAST_INSERT_ID()")
    long insertedId();
}
