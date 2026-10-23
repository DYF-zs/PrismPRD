package com.prismprd.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AppUserMapper {

    void insert(AppUser user);

    AppUser selectById(@Param("id") UUID id);

    AppUser selectByDevSubject(@Param("subject") String subject);

    List<AppUser> selectByIds(@Param("ids") List<UUID> ids);

    int updateDevProfile(@Param("id") UUID id, @Param("username") String username,
                         @Param("displayName") String displayName, @Param("email") String email);
}
