package com.prismprd.prd.testcase;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TestCaseMapper {

    void insertBatch(@Param("list") List<TestCase> cases);

    void deleteByVersion(@Param("versionId") UUID versionId);

    List<TestCase> selectByVersion(@Param("versionId") UUID versionId);

    String selectReport(@Param("versionId") UUID versionId);
    void saveReport(@Param("versionId") UUID versionId, @Param("result") String result);
    UUID selectAcceptancePatch(@Param("generationId") UUID generationId, @Param("index") int index);
    void linkAcceptancePatch(@Param("generationId") UUID generationId, @Param("index") int index, @Param("patchId") UUID patchId);
    List<Integer> selectAppliedIssues(@Param("generationId") UUID generationId);
}
