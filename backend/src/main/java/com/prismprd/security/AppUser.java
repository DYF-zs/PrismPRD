package com.prismprd.security;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class AppUser {

    private UUID id;
    private String authSource;
    private String issuer;
    private String subject;
    private String username;
    private String displayName;
    private String email;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
