package com.example.apartment.support.audit;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditLogMapper {
    String BATCH_CHANGES=" FROM operation_log WHERE target_type='BATCH' AND target_id=#{id} AND action IN "
            + "('BATCH_ROOM_ADDED','BATCH_ROOM_REMOVED','BATCH_CLOSED','BATCH_REOPENED','BATCH_SUSPENDED','BATCH_RESTORED','BATCH_EXPIRED')";
    @Select("SELECT id,actor_id,action,before_data,after_data,created_at"+BATCH_CHANGES+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    java.util.List<BatchChangeQuery.Row> batchChanges(long id,int size,long offset);
    @Select("SELECT COUNT(*)"+BATCH_CHANGES) long countBatchChanges(long id);
    @Insert("INSERT INTO operation_log(actor_id, action, target_type, target_id, before_data, after_data) "
            + "VALUES (#{actor}, #{action}, #{type}, #{target}, #{before}, #{after})")
    void insert(@Param("actor") long actor, @Param("action") String action, @Param("type") String type,
            @Param("target") long target, @Param("before") String before, @Param("after") String after);
}
