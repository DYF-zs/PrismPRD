package com.prismprd.security;

import java.util.UUID;

public record PrismPrincipal(UUID userId, String username, String displayName, String authSource) {
}
