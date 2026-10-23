package com.prismprd.prd.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PrdMemberMapper {

    void insert(PrdMember member);

    List<PrdMemberView> selectByPrd(@Param("prdId") UUID prdId);

    String selectRole(@Param("prdId") UUID prdId, @Param("userId") UUID userId);

    int deleteByPrd(@Param("prdId") UUID prdId);

    int deleteByPrdAndUser(@Param("prdId") UUID prdId, @Param("userId") UUID userId);
}
