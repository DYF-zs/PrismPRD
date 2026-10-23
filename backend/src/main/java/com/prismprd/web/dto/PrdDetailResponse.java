package com.prismprd.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PrdDetailResponse(
        UUID id,
        String title,
        String status,
        UUID ownerId,
        String ownerName,
        String draftJson,
        String draftMarkdown,
        long revision,
        UUID currentVersionId,
        Integer currentVersionNo,
        BigDecimal currentScore,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
