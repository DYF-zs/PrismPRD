package com.prismprd.prd.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PrdDocumentMapper {

    void insert(PrdDocument document);

    PrdDocument selectById(@Param("id") UUID id);
    UUID lockById(@Param("id") UUID id);

    List<PrdDocumentSummary> selectPage(@Param("userId") UUID userId,
                                        @Param("query") String query,
                                        @Param("status") String status,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    long countPage(@Param("userId") UUID userId,
                   @Param("query") String query,
                   @Param("status") String status);

    int updateDraft(@Param("id") UUID id,
                    @Param("revision") long revision,
                    @Param("draftJson") String draftJson,
                    @Param("draftMarkdown") String draftMarkdown);

    int updateMeta(@Param("id") UUID id, @Param("title") String title, @Param("status") String status);

    int updateOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    int updateCurrentVersion(@Param("id") UUID id, @Param("versionId") UUID versionId);

    int updateReviewOutcome(@Param("id") UUID id,
                            @Param("versionId") UUID versionId,
                            @Param("status") String status,
                            @Param("currentScore") BigDecimal currentScore);

    String resolveUserRole(@Param("prdId") UUID prdId, @Param("userId") UUID userId);
}
