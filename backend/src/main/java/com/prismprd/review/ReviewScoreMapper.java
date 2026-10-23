package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewScoreMapper {

    void insertBatch(@Param("list") List<ReviewScore> scores);

    List<ReviewScore> selectByReportId(@Param("reportId") UUID reportId);
}
