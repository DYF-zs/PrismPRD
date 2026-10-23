package com.prismprd.web.dto;

import java.util.UUID;

public record MeResponse(UUID userId, String username, String displayName, String email, String authSource) {
}
