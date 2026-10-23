package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewFindingMapper {

    void insertBatch(@Param("list") List<ReviewFinding> findings);

    List<ReviewFinding> selectByReportId(@Param("reportId") UUID reportId);

    ReviewFinding selectById(@Param("id") UUID id);

    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
