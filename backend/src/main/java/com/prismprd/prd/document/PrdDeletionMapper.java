package com.prismprd.prd.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface PrdDeletionMapper {
    int deleteDocument(@Param("prdId") UUID prdId);
    int deleteReview(@Param("reviewId") UUID reviewId);
    int deleteReviewSuggestions(@Param("reviewId") UUID reviewId);
    int refreshReviewOutcome(@Param("prdId") UUID prdId, @Param("versionId") UUID versionId);
}
