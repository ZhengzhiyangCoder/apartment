package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SchemeMapper {
    String COLUMNS = "SELECT s.id, s.name, s.threshold, s.status, s.version, s.source_id, s.created_at, s.updated_at, s.activated_at,"
            + " (SELECT COALESCE(SUM(d.weight), 0) FROM hygiene_scheme_dimension d WHERE d.scheme_id = s.id) AS total_weight,"
            + " (SELECT COUNT(*) FROM hygiene_scheme_dimension d WHERE d.scheme_id = s.id) AS dimension_count FROM hygiene_scheme s";
    String FILTER = " WHERE (#{publishedOnly} = false OR s.status <> 'DRAFT') AND (#{status} IS NULL OR s.status = #{status})"
            + " AND (#{q} = '' OR LOCATE(#{q}, s.name) > 0)";
    @Select(COLUMNS + FILTER + " ORDER BY s.id DESC LIMIT #{size} OFFSET #{offset}")
    List<SchemeViews.Scheme> list(boolean publishedOnly, String q, SchemeViews.Status status, int size, long offset);
    @Select("SELECT COUNT(*) FROM hygiene_scheme s" + FILTER)
    long count(boolean publishedOnly, String q, SchemeViews.Status status);
    @Select(COLUMNS + " WHERE s.id = #{id}") SchemeViews.Scheme find(long id);
    @Select(COLUMNS + " WHERE s.status = 'ACTIVE'") SchemeViews.Scheme active();
    @Select("SELECT id, name, weight, sort_order FROM hygiene_scheme_dimension WHERE scheme_id = #{id} ORDER BY sort_order")
    List<SchemeViews.Dimension> dimensions(long id);
    @Insert("INSERT INTO hygiene_scheme(name, threshold, source_id) VALUES(#{name}, #{threshold}, #{sourceId})")
    void create(String name, BigDecimal threshold, Long sourceId);
    @Select("SELECT LAST_INSERT_ID()") long insertedId();
    @Insert("INSERT INTO hygiene_scheme_dimension(scheme_id, name, weight, sort_order) VALUES(#{id}, #{name}, #{weight}, #{position})")
    void insertDimension(long id, String name, BigDecimal weight, int position);
    @Update("UPDATE hygiene_scheme SET name = #{name}, threshold = #{threshold}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE id = #{id} AND version = #{version} AND status = 'DRAFT'")
    int update(long id, long version, String name, BigDecimal threshold);
    @Delete("DELETE FROM hygiene_scheme_dimension WHERE scheme_id = #{id}") void clearDraftDimensions(long id);
    @Update("UPDATE hygiene_scheme SET status = 'ACTIVE', version = version + 1, activated_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE id = #{id} AND version = #{version} AND status = 'DRAFT'")
    int activate(long id, long version);
    @Update("UPDATE hygiene_scheme SET status = 'ARCHIVED', version = version + 1, updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE id = #{id} AND version = #{version} AND status = 'ACTIVE'")
    int archive(long id, long version);
}
