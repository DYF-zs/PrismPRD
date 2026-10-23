package com.prismprd.prd.patch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PrdPatchMapper {

    void insert(PrdPatch patch);

    PrdPatch selectById(@Param("id") UUID id);
    PrdPatch selectByIdForUpdate(@Param("id") UUID id);
    List<PrdPatch> selectUnversionedApplied(@Param("prdId") UUID prdId, @Param("revision") long revision);
    List<PrdPatch> selectByVersion(@Param("versionId") UUID versionId);
    void linkVersion(@Param("versionId") UUID versionId, @Param("patchId") UUID patchId);

    List<PrdPatch> selectByPrd(@Param("prdId") UUID prdId);

    List<PrdPatch> selectPendingByFinding(@Param("findingId") UUID findingId);

    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
