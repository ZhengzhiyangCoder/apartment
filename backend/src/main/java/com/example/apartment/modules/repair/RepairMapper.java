package com.example.apartment.modules.repair;

import com.example.apartment.modules.housing.ResidencyView;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RepairMapper {
    String COLUMNS = "SELECT id, submitted_by, student_name, residency_id, student_id, building_id, building_name, room_id, room_no, bed_id, bed_no, category, description, status, created_at, updated_at, assignee_id, assignee_name, assigned_by, assigned_by_name, assigned_at, started_at, completed_at, completion_note, version FROM repair_order";
    String FILTER = " WHERE (#{owner} IS NULL OR submitted_by = #{owner}) AND (#{assignee} IS NULL OR assignee_id = #{assignee}) AND (#{status} IS NULL OR status = #{status})"
            + " AND (#{q} = '' OR LOCATE(#{q}, description) > 0 OR LOCATE(#{q}, building_name) > 0 OR LOCATE(#{q}, room_no) > 0 OR LOCATE(#{q}, student_name) > 0)";
    @Select(COLUMNS + FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<RepairViews.Order> list(Long owner, Long assignee, String q, RepairViews.Status status, int size, long offset);
    @Select("SELECT COUNT(*) FROM repair_order" + FILTER)
    long count(Long owner, Long assignee, String q, RepairViews.Status status);
    @Select(COLUMNS + " WHERE id = #{id}")
    RepairViews.Order find(long id);
    @Update("UPDATE repair_order SET status = 'PENDING', assignee_id = #{worker.id}, assignee_name = #{worker.displayName},"
            + " assigned_by = #{actor.id}, assigned_by_name = #{actor.displayName}, assigned_at = CURRENT_TIMESTAMP(6),"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{before.id} AND version = #{before.version} AND status = #{before.status}")
    int assign(RepairViews.Order before, com.example.apartment.modules.identity.AccountAccess.Maintainer worker,
            com.example.apartment.modules.identity.AccountView actor);
    @Update("UPDATE repair_order SET status = 'IN_PROGRESS', started_at = CURRENT_TIMESTAMP(6), version = version + 1,"
            + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND version = #{version} AND status = 'PENDING'")
    int start(long id, long version);
    @Update("UPDATE repair_order SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP(6), completion_note = #{note},"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND version = #{version} AND status = 'IN_PROGRESS'")
    int complete(long id, long version, String note);
    @Insert("INSERT INTO repair_status_log(order_id, from_status, to_status, actor_id, actor_name, note, previous_assignee_id, previous_assignee_name, assignee_id, assignee_name)"
            + " VALUES(#{before.id}, #{before.status}, #{after.status}, #{actor.id}, #{actor.displayName}, #{note}, #{before.assigneeId}, #{before.assigneeName}, #{after.assigneeId}, #{after.assigneeName})")
    void workflowLog(RepairViews.Order before, RepairViews.Order after, com.example.apartment.modules.identity.AccountView actor, String note);
    @Select("SELECT id FROM repair_order WHERE submitted_by = #{owner} AND request_id = #{key}")
    Long previous(long owner, String key);
    @Select("SELECT request_hash FROM repair_order WHERE id = #{id}")
    String requestHash(long id);
    @Insert("INSERT INTO repair_order(submitted_by, residency_id, student_id, building_id, room_id, bed_id, student_name, building_name, room_no, bed_no, category, description, request_id, request_hash)"
            + " VALUES(#{owner}, #{r.id}, #{r.studentId}, #{r.buildingId}, #{r.roomId}, #{r.bedId}, #{r.fullName}, #{r.buildingName}, #{r.roomNo}, #{r.bedNo}, #{category}, #{description}, #{key}, #{hash})")
    void insert(long owner, ResidencyView r, RepairViews.Category category, String description, String key, String hash);
    @Select("SELECT LAST_INSERT_ID()") long insertedId();
    @Insert("INSERT INTO repair_image(order_id, storage_key, byte_size) VALUES(#{order}, #{key}, #{size})")
    void insertImage(long order, String key, long size);
    @Select("SELECT id, order_id, byte_size FROM repair_image WHERE order_id = #{order} ORDER BY id")
    List<RepairViews.Image> images(long order);
    @Select("SELECT storage_key FROM repair_image WHERE order_id = #{order} AND id = #{id}")
    String imageKey(long order, long id);
    @Insert("INSERT INTO repair_status_log(order_id, from_status, to_status, actor_id, actor_name, note) VALUES(#{order}, #{from}, #{to}, #{actor}, #{actorName}, #{note})")
    void log(long order, RepairViews.Status from, RepairViews.Status to, long actor, String actorName, String note);
    @Select("SELECT id, from_status, to_status, actor_id, actor_name, note, created_at, previous_assignee_id, previous_assignee_name, assignee_id, assignee_name FROM repair_status_log WHERE order_id = #{order} ORDER BY id")
    List<RepairViews.Log> logs(long order);
    @Update("UPDATE repair_order SET status = 'CANCELLED', version = version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND status IN ('PENDING_ASSIGNMENT', 'PENDING')")
    int cancel(long id);
    @Select("SELECT COUNT(*) FROM repair_order WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND (submitted_by = #{id} OR assignee_id = #{id})")
    long openByAccount(long id);
    @Select("SELECT COUNT(*) FROM repair_order WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND student_id = #{id}")
    long openByStudent(long id);
    @Select("SELECT COUNT(*) FROM repair_order WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND building_id = #{id}")
    long openByBuilding(long id);
    @Select("SELECT COUNT(*) FROM repair_order WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND room_id = #{id}")
    long openByRoom(long id);
    @Select("SELECT COUNT(*) FROM repair_order WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND bed_id = #{id}")
    long openByBed(long id);
}
