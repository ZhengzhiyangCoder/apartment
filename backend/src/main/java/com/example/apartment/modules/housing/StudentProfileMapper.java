package com.example.apartment.modules.housing;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StudentProfileMapper {
    String COLUMNS = "SELECT id, user_id, student_no, full_name, college, major, class_name, enabled, version FROM student_profile";
    String FILTER = " WHERE (#{q} = '' OR LOCATE(#{q}, student_no) > 0 OR LOCATE(#{q}, full_name) > 0 OR LOCATE(#{q}, college) > 0 OR LOCATE(#{q}, class_name) > 0) AND (#{enabled} IS NULL OR enabled = #{enabled})";
    @Select(COLUMNS + FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<StudentProfile> list(String q, Boolean enabled, int size, long offset);
    @Select("SELECT COUNT(*) FROM student_profile" + FILTER)
    long count(String q, Boolean enabled);
    @Select(COLUMNS + " WHERE id = #{id} FOR UPDATE")
    StudentProfile lock(long id);
    @Select("SELECT COUNT(*) FROM student_profile WHERE user_id = #{userId}")
    long countByUser(long userId);
    @Insert("INSERT INTO student_profile(user_id, student_no, full_name, college, major, class_name) VALUES (#{userId}, #{studentNo}, #{fullName}, #{college}, #{major}, #{className})")
    void create(long userId, String studentNo, String fullName, String college, String major, String className);
    @Select("SELECT LAST_INSERT_ID()")
    long insertedId();
    @Update("UPDATE student_profile SET student_no = #{studentNo}, full_name = #{fullName}, college = #{college}, major = #{major}, class_name = #{className}, version = version + 1 WHERE id = #{id}")
    void update(long id, String studentNo, String fullName, String college, String major, String className);
    @Update("UPDATE student_profile SET enabled = #{enabled}, version = version + 1 WHERE id = #{id}")
    void enable(long id, boolean enabled);
}
