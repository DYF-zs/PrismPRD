package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.prismprd.web.dto.ReviewJobSummary;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewJobMapper {

    void insert(ReviewJob job);

    ReviewJob selectById(@Param("id") UUID id);
    int countActive(@Param("prdId") UUID prdId);
    int markInterrupted();

    int markRunning(@Param("id") UUID id);

    int markSuccess(@Param("id") UUID id);

    int markFailed(@Param("id") UUID id, @Param("errorMessage") String errorMessage);

    List<ReviewJobSummary> selectByPrd(@Param("prdId") UUID prdId);
}
