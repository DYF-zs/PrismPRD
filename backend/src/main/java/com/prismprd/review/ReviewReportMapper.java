package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface ReviewReportMapper {

    void insert(ReviewReport report);

    ReviewReport selectByJobId(@Param("reviewJobId") UUID reviewJobId);

    ReviewReport selectById(@Param("id") UUID id);
}
