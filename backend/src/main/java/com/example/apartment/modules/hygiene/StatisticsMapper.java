package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StatisticsMapper {
    record BuildingRow(long buildingId,String buildingName,BigDecimal score,long participants,long samples,long expected) {}
    record SummaryRow(long participants,long samples,long expected) {}
    record TrendRow(long buildingId,String buildingName,LocalDate start,BigDecimal score,long participants,long samples) {}
    record DimensionRow(long schemeId,String schemeName,long buildingId,String buildingName,long dimensionId,String name,BigDecimal weight,BigDecimal score,long samples) {}
    // Count the adjusted expected-room snapshot, never current occupancy. Missing scores stay NULL.
    String ROOMS = "WITH expected AS (SELECT r.*,s.total_score,s.id AS score_id,"
            + " FIRST_VALUE(r.building_name) OVER(PARTITION BY r.building_id ORDER BY b.week_start DESC,r.room_id) AS building_label,"
            + " ROW_NUMBER() OVER(PARTITION BY r.room_id ORDER BY b.week_start DESC) AS recent"
            + " FROM hygiene_batch_room r JOIN hygiene_batch b ON b.id=r.batch_id"
            + " LEFT JOIN hygiene_score s ON s.batch_id=r.batch_id AND s.room_id=r.room_id"
            + " WHERE"+ResultMapper.VALID_PERIOD
            + " AND (#{buildingId} IS NULL OR r.building_id=#{buildingId})), rooms AS ("
            + " SELECT building_id,room_id,MAX(building_label) AS building_name,"
            + " MAX(CASE WHEN recent=1 THEN room_no END) AS room_no,"+ResultMapper.AVERAGE+" AS score,"
            + " COUNT(score_id) AS samples,COUNT(*) AS expected FROM expected GROUP BY building_id,room_id)";
    @Select(ROOMS+" SELECT building_id,MAX(building_name) AS building_name,ROUND(AVG(score),2) AS score,"
            + " COUNT(score) AS participants,SUM(samples) AS samples,SUM(expected) AS expected FROM rooms GROUP BY building_id ORDER BY building_name,building_id")
    List<BuildingRow> buildings(Long buildingId,LocalDate start,LocalDate end);
    @Select(ROOMS+" SELECT COUNT(score) AS participants,COALESCE(SUM(samples),0) AS samples,COALESCE(SUM(expected),0) AS expected FROM rooms WHERE (#{roomId} IS NULL OR room_id=#{roomId})")
    SummaryRow summary(Long buildingId,Long roomId,LocalDate start,LocalDate end);
    // Calculate each building's ranks before selecting one room, so its student-visible rank is unchanged.
    @Select(ROOMS+", ranked AS (SELECT *,RANK() OVER(PARTITION BY building_id ORDER BY score DESC) AS ranking FROM rooms WHERE samples>0)"
            + " SELECT room_id,building_id,building_name,room_no,score,samples,ranking FROM ranked WHERE (#{roomId} IS NULL OR room_id=#{roomId})"
            + " ORDER BY building_name,building_id,score DESC,room_no,room_id LIMIT #{size} OFFSET #{offset}")
    List<StatisticsViews.Ranking> rankings(Long buildingId,Long roomId,LocalDate start,LocalDate end,int size,long offset);

    @Select("WITH effective AS (SELECT r.building_id,r.room_id,r.building_name,s.total_score,"
            + " CASE WHEN #{kind}='MONTH' THEN CAST(DATE_FORMAT(b.week_start,'%Y-%m-01') AS DATE) ELSE b.week_start END AS period_start"
            + ResultMapper.EFFECTIVE+" AND (#{buildingId} IS NULL OR r.building_id=#{buildingId}) AND (#{roomId} IS NULL OR r.room_id=#{roomId})),"
            + " periods AS (SELECT building_id,room_id,period_start,MAX(building_name) AS building_name,"+ResultMapper.AVERAGE
            + " AS score,COUNT(*) AS samples FROM effective GROUP BY building_id,room_id,period_start)"
            + " SELECT building_id,MAX(building_name) AS building_name,period_start,ROUND(AVG(score),2) AS score,COUNT(*) AS participants,SUM(samples) AS samples"
            + " FROM periods GROUP BY building_id,period_start ORDER BY building_id,period_start")
    List<TrendRow> trend(Long buildingId,Long roomId,String kind,LocalDate start,LocalDate end);

    @Select("SELECT b.scheme_id,MAX(b.scheme_name) AS scheme_name,r.building_id,MAX(r.building_name) AS building_name,"
            + " d.dimension_id,d.name,d.weight,ROUND(AVG(d.score),2) AS score,COUNT(*) AS samples"
            + " FROM hygiene_score s JOIN hygiene_batch b ON b.id=s.batch_id"
            + " JOIN hygiene_batch_room r ON r.batch_id=s.batch_id AND r.room_id=s.room_id JOIN hygiene_score_dimension d ON d.score_id=s.id"
            + " WHERE"+ResultMapper.VALID_PERIOD
            + " AND (#{buildingId} IS NULL OR r.building_id=#{buildingId}) AND (#{roomId} IS NULL OR r.room_id=#{roomId})"
            + " GROUP BY b.scheme_id,r.building_id,d.dimension_id,d.name,d.weight,d.sort_order ORDER BY b.scheme_id,r.building_id,d.sort_order,d.dimension_id")
    List<DimensionRow> dimensions(Long buildingId,Long roomId,LocalDate start,LocalDate end);
}
