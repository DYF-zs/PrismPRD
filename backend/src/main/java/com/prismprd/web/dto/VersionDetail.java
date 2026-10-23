package com.prismprd.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record VersionDetail(
        UUID id,
        UUID prdId,
        int versionNo,
        String editorJson,
        String markdownSnapshot,
        String source,
        String changeSummary,
        UUID createdBy,
        OffsetDateTime createdAt,
        Long draftRevision,
        List<PatchDetail> patches) {
}
