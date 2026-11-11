package com.example.apartment.modules.identity;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountAdministrationMapper {
    String FILTER = " WHERE (#{q} = '' OR LOCATE(#{q}, username) > 0 OR LOCATE(#{q}, display_name) > 0)"
            + " AND (#{role} IS NULL OR role = #{role}) AND (#{enabled} IS NULL OR enabled = #{enabled})";

    @Select("SELECT id, username, display_name, role, enabled, auth_version AS version FROM sys_user" + FILTER
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<AccountView> list(@Param("q") String q, @Param("role") Role role, @Param("enabled") Boolean enabled,
            @Param("size") int size, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM sys_user" + FILTER)
    long count(@Param("q") String q, @Param("role") Role role, @Param("enabled") Boolean enabled);

    @Insert("INSERT INTO sys_user(username, display_name, role, password_hash) VALUES (#{username}, #{displayName}, #{role}, #{hash})")
    void create(@Param("username") String username, @Param("displayName") String displayName,
            @Param("role") Role role, @Param("hash") String hash);

    @Select("SELECT id FROM sys_user WHERE role = 'SYSTEM_ADMIN' AND enabled = true ORDER BY id FOR UPDATE")
    List<Long> lockEnabledAdministrators();

    @Update("UPDATE sys_user SET role = #{role}, auth_version = auth_version + 1 WHERE id = #{id}")
    void changeRole(@Param("id") long id, @Param("role") Role role);

    @Update("UPDATE sys_user SET enabled = #{enabled}, auth_version = auth_version + 1 WHERE id = #{id}")
    void changeEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("UPDATE sys_user SET password_hash = #{hash}, auth_version = auth_version + 1 WHERE id = #{id}")
    void resetPassword(@Param("id") long id, @Param("hash") String hash);
}
