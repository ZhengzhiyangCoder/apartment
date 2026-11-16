package com.example.apartment.modules.identity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper {
    @Select("SELECT id, username, display_name FROM sys_user WHERE role = 'MAINTAINER' AND enabled = true"
            + " AND (#{q} = '' OR LOCATE(#{q}, username) > 0 OR LOCATE(#{q}, display_name) > 0) ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    java.util.List<AccountAccess.Maintainer> enabledMaintainers(String q, int size, long offset);
    @Select("SELECT id, username, password_hash, display_name, role, enabled, auth_version FROM sys_user WHERE username = #{username}")
    UserAccount findByUsername(String username);

    @Select("SELECT id, username, password_hash, display_name, role, enabled, auth_version FROM sys_user WHERE id = #{id}")
    UserAccount findById(long id);

    @Select("SELECT id, username, password_hash, display_name, role, enabled, auth_version FROM sys_user WHERE id = #{id} FOR UPDATE")
    UserAccount lockById(long id);

    @Update("UPDATE sys_user SET password_hash = #{hash}, auth_version = auth_version + 1 WHERE id = #{id} AND auth_version = #{version} AND enabled = true")
    int changePassword(@Param("id") long id, @Param("version") long version, @Param("hash") String hash);

    @Select("<script>SELECT id, username, enabled FROM sys_user WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    java.util.List<AccountSummary> summaries(@Param("ids") java.util.List<Long> ids);
}
