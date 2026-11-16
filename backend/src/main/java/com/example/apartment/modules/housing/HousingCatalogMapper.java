package com.example.apartment.modules.housing;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.example.apartment.modules.housing.HousingViews.*;

@Mapper
public interface HousingCatalogMapper {
    String BUILDING_FILTER = " WHERE (#{q} = '' OR LOCATE(#{q}, name) > 0) AND (#{enabled} IS NULL OR enabled = #{enabled})";
    @Select("SELECT id, name, enabled, version FROM dorm_building" + BUILDING_FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Building> buildings(String q, Boolean enabled, int size, long offset);
    @Select("SELECT COUNT(*) FROM dorm_building" + BUILDING_FILTER)
    long countBuildings(String q, Boolean enabled);
    @Select("SELECT id, name, enabled, version FROM dorm_building WHERE id = #{id} FOR UPDATE")
    Building building(long id);
    @Select("SELECT id, name, enabled, version FROM dorm_building WHERE id = #{id}")
    Building readBuilding(long id);
    @Insert("INSERT INTO dorm_building(name) VALUES (#{name})")
    void createBuilding(String name);
    @Select("SELECT LAST_INSERT_ID()")
    long insertedId();
    @Update("UPDATE dorm_building SET name = #{name}, version = version + 1 WHERE id = #{id}")
    void updateBuilding(long id, String name);
    @Update("UPDATE dorm_building SET enabled = #{enabled}, version = version + 1 WHERE id = #{id}")
    void enableBuilding(long id, boolean enabled);

    String ROOM_FROM = " FROM dorm_room r JOIN dorm_building b ON b.id = r.building_id";
    String ROOM_COLUMNS = "SELECT r.id, r.building_id, b.name AS building_name, b.enabled AS building_enabled, r.room_no, r.floor, r.enabled, r.version";
    String ROOM_FILTER = " WHERE (#{q} = '' OR LOCATE(#{q}, r.room_no) > 0 OR LOCATE(#{q}, b.name) > 0)"
            + " AND (#{buildingId} IS NULL OR r.building_id = #{buildingId}) AND (#{enabled} IS NULL OR r.enabled = #{enabled})";
    @Select(ROOM_COLUMNS + ROOM_FROM + ROOM_FILTER + " ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Room> rooms(String q, Long buildingId, Boolean enabled, int size, long offset);
    @Select("SELECT COUNT(*)" + ROOM_FROM + ROOM_FILTER)
    long countRooms(String q, Long buildingId, Boolean enabled);
    @Select(ROOM_COLUMNS + ROOM_FROM + " WHERE r.id = #{id} FOR UPDATE")
    Room room(long id);
    @Select(ROOM_COLUMNS + ROOM_FROM + " WHERE r.id = #{id}")
    Room readRoom(long id);
    @Insert("INSERT INTO dorm_room(building_id, room_no, floor) VALUES (#{buildingId}, #{roomNo}, #{floor})")
    void createRoom(long buildingId, String roomNo, int floor);
    @Update("UPDATE dorm_room SET room_no = #{roomNo}, floor = #{floor}, version = version + 1 WHERE id = #{id}")
    void updateRoom(long id, String roomNo, int floor);
    @Update("UPDATE dorm_room SET enabled = #{enabled}, version = version + 1 WHERE id = #{id}")
    void enableRoom(long id, boolean enabled);

    String BED_FROM = " FROM dorm_bed d JOIN dorm_room r ON r.id = d.room_id JOIN dorm_building b ON b.id = r.building_id";
    String BED_COLUMNS = "SELECT d.id, d.room_id, r.building_id, b.name AS building_name, r.room_no, b.enabled AS building_enabled, r.enabled AS room_enabled, d.bed_no, d.enabled, d.version";
    String BED_FILTER = " WHERE (#{q} = '' OR LOCATE(#{q}, d.bed_no) > 0 OR LOCATE(#{q}, r.room_no) > 0 OR LOCATE(#{q}, b.name) > 0)"
            + " AND (#{roomId} IS NULL OR d.room_id = #{roomId}) AND (#{buildingId} IS NULL OR r.building_id = #{buildingId})"
            + " AND (#{enabled} IS NULL OR d.enabled = #{enabled})";
    @Select(BED_COLUMNS + BED_FROM + BED_FILTER + " ORDER BY d.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Bed> beds(String q, Long buildingId, Long roomId, Boolean enabled, int size, long offset);
    @Select("SELECT COUNT(*)" + BED_FROM + BED_FILTER)
    long countBeds(String q, Long buildingId, Long roomId, Boolean enabled);
    @Select(BED_COLUMNS + BED_FROM + " WHERE d.id = #{id}")
    Bed readBed(long id);
    @Select(BED_COLUMNS + BED_FROM + " WHERE d.id = #{id} FOR UPDATE")
    Bed bed(long id);
    @Insert("INSERT INTO dorm_bed(room_id, bed_no) VALUES (#{roomId}, #{bedNo})")
    void createBed(long roomId, String bedNo);
    @Update("UPDATE dorm_bed SET bed_no = #{bedNo}, version = version + 1 WHERE id = #{id}")
    void updateBed(long id, String bedNo);
    @Update("UPDATE dorm_bed SET enabled = #{enabled}, version = version + 1 WHERE id = #{id}")
    void enableBed(long id, boolean enabled);
}
