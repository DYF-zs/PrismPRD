package com.prismprd.web.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ChatSessionSummary {

    private UUID id;
    private UUID prdId;
    private String title;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
