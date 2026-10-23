package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewDimensionMapper {

    List<ReviewDimension> selectByRubric(@Param("rubricId") UUID rubricId);
    void insert(ReviewDimension dimension);
}
