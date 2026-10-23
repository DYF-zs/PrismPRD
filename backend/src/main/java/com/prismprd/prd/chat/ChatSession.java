package com.prismprd.prd.chat;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ChatSession {

    private UUID id;
    private UUID prdId;
    private String title;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
