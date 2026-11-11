package com.example.apartment.support.audit;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditLogMapper {
    @Insert("INSERT INTO operation_log(actor_id, action, target_type, target_id, before_data, after_data) "
            + "VALUES (#{actor}, #{action}, #{type}, #{target}, #{before}, #{after})")
    void insert(@Param("actor") long actor, @Param("action") String action, @Param("type") String type,
            @Param("target") long target, @Param("before") String before, @Param("after") String after);
}
