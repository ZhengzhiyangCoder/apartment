package com.example.apartment.modules.hygiene;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.example.apartment.modules.hygiene.CorrectionViews.*;

@Mapper
public interface CorrectionMapper {
    @Select("SELECT id,batch_id,room_id,total_score,threshold,submitted_by,submitted_at,version FROM hygiene_score WHERE id=#{id}")
    BatchViews.ScoreRow find(long id);
    @Update("UPDATE hygiene_score SET total_score=#{total},version=version+1 WHERE id=#{id} AND version=#{version}")
    int update(long id,long version,BigDecimal total);
    @Update("UPDATE hygiene_score_dimension SET score=#{score} WHERE score_id=#{id} AND dimension_id=#{dimensionId}")
    void dimension(long id,long dimensionId,int score);
    @Insert("INSERT INTO hygiene_score_correction(score_id,version,actor_id,actor_name,reason,before_data,after_data,created_at) VALUES(#{id},#{version},#{actor},#{actorName},#{reason},#{before},#{after},#{now})")
    void record(long id,long version,long actor,String actorName,String reason,String before,String after,Instant now);
    @Select("SELECT id,score_id,version,actor_id,actor_name,reason,before_data,after_data,created_at FROM hygiene_score_correction WHERE score_id=#{id} ORDER BY version DESC LIMIT #{size} OFFSET #{offset}")
    List<Row> history(long id,int size,long offset);
    @Select("SELECT COUNT(*) FROM hygiene_score_correction WHERE score_id=#{id}") long count(long id);
}
