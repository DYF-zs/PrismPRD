package com.prismprd.web.dto;

import java.time.OffsetDateTime;

public record SaveDraftResponse(long revision, OffsetDateTime updatedAt) {
}
