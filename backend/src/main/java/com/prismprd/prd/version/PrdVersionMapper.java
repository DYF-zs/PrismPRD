package com.prismprd.prd.version;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PrdVersionMapper {

    void insert(PrdVersion version);

    PrdVersion selectById(@Param("id") UUID id);
    boolean exists(@Param("id") UUID id);

    List<PrdVersion> selectByPrd(@Param("prdId") UUID prdId);
}
