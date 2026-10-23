package com.prismprd.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VersionSummary(
        UUID id,
        UUID prdId,
        int versionNo,
        String source,
        String changeSummary,
        UUID createdBy,
        OffsetDateTime createdAt) {
}
