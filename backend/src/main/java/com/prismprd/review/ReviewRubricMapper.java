package com.prismprd.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewRubricMapper {

    List<ReviewRubric> selectEnabledLatest();

    ReviewRubric selectById(@Param("id") UUID id);

    ReviewRubric selectLatestByKey(@Param("rubricKey") String rubricKey);
    ReviewRubric lockById(@Param("id") UUID id);
    List<ReviewRubric> selectVersions(@Param("rubricKey") String rubricKey);
    void insert(ReviewRubric rubric);
}
