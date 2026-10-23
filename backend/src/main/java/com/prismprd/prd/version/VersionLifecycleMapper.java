package com.prismprd.prd.version;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface VersionLifecycleMapper {
    int useVersion(@Param("prdId") UUID prdId, @Param("versionId") UUID versionId, @Param("revision") long revision);
    int staleSuggestions(@Param("prdId") UUID prdId);
    int clearBaseline(@Param("prdId") UUID prdId);
    int deleteSuggestions(@Param("versionId") UUID versionId);
    int deleteReviews(@Param("versionId") UUID versionId);
    int deleteVersion(@Param("versionId") UUID versionId);
}
