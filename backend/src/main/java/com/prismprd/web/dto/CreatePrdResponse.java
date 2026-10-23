package com.prismprd.web.dto;

import java.util.UUID;

public record CreatePrdResponse(UUID prdId, UUID defaultSessionId, long revision) {
}
