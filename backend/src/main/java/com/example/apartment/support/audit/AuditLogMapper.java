package com.example.apartment.support.audit;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditLogMapper {
    String SEARCH = " FROM operation_log WHERE 1=1"
        + "<if test='actorId != null'> AND actor_id=#{actorId}</if>"
        + "<if test='targetType != null'> AND target_type=#{targetType}</if>"
        + "<if test='targetId != null'> AND target_id=#{targetId}</if>"
        + "<if test='action != null'> AND action=#{action}</if>"
        + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
        + "<if test='until != null'> AND created_at &lt; #{until}</if>";
    @Select("<script>SELECT id,actor_id,action,target_type,target_id,created_at,NULL AS before_data,NULL AS after_data"+SEARCH
        + "<choose><when test='sort.name() == &quot;OLDEST&quot;'> ORDER BY created_at ASC,id ASC</when>"
        + "<otherwise> ORDER BY created_at DESC,id DESC</otherwise></choose> LIMIT #{size} OFFSET #{offset}</script>")
    java.util.List<AuditQuery.Row> search(AuditQuery.Filter filter);
    @Select("<script>SELECT COUNT(*)"+SEARCH+"</script>") long countSearch(AuditQuery.Filter filter);
    @Select("<script>SELECT id,actor_id,action,target_type,target_id,created_at,before_data,after_data FROM operation_log WHERE id=#{id}"
        + "<if test='owner != null'> AND actor_id=#{owner}</if></script>")
    AuditQuery.Row auditRecord(long id,Long owner);

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
