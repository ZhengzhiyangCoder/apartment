package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** Formal current values only; period membership always follows the batch Monday, never submission time. */
@Mapper
public interface ResultMapper {
    record RankedRoom(long roomId,String roomNo,BigDecimal score,long samples,long rank) {}
    record TrendRow(LocalDate start,BigDecimal score,long samples) {}
    String EFFECTIVE = " FROM hygiene_score s JOIN hygiene_batch b ON b.id=s.batch_id"
            + " JOIN hygiene_batch_room r ON r.batch_id=s.batch_id AND r.room_id=s.room_id"
            + " WHERE b.status <> 'SUSPENDED' AND b.week_start >= #{start} AND b.week_start < #{end}";
    String AVERAGE = "ROUND(AVG(total_score),2)";
    String RANKED = "WITH effective AS (SELECT s.room_id,r.room_no,s.total_score,"
            + " ROW_NUMBER() OVER(PARTITION BY s.room_id ORDER BY b.week_start DESC) AS recent" + EFFECTIVE
            + " AND r.building_id=#{buildingId}), totals AS ("
            + " SELECT room_id,MAX(CASE WHEN recent=1 THEN room_no END) AS room_no," + AVERAGE
            + " AS score,COUNT(*) AS samples FROM effective GROUP BY room_id), ranked AS ("
            + " SELECT room_id,room_no,score,samples,RANK() OVER(ORDER BY score DESC) AS ranking FROM totals)";
    @Select(RANKED+" SELECT room_id,room_no,score,samples,ranking FROM ranked ORDER BY score DESC,room_no,room_id LIMIT #{size} OFFSET #{offset}")
    List<RankedRoom> rankings(long buildingId,LocalDate start,LocalDate end,int size,long offset);
    @Select(RANKED+" SELECT room_id,room_no,score,samples,ranking FROM ranked WHERE room_id=#{roomId}")
    RankedRoom own(long buildingId,long roomId,LocalDate start,LocalDate end);
    @Select("SELECT COUNT(DISTINCT s.room_id)"+EFFECTIVE+" AND r.building_id=#{buildingId}")
    long participants(long buildingId,LocalDate start,LocalDate end);
    @Select("SELECT CASE WHEN #{kind}='MONTH' THEN CAST(DATE_FORMAT(b.week_start,'%Y-%m-01') AS DATE) ELSE b.week_start END AS period_start,"
            + AVERAGE+" AS score,COUNT(*) AS samples"+EFFECTIVE+" AND s.room_id=#{roomId} GROUP BY period_start ORDER BY period_start")
    List<TrendRow> trend(long roomId,String kind,LocalDate start,LocalDate end);
}
