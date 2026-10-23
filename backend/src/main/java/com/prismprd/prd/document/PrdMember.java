package com.prismprd.prd.document;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PrdMember {

    private UUID id;
    private UUID prdId;
    private UUID userId;
    private String role;
    private UUID createdBy;
    private OffsetDateTime createdAt;
}
