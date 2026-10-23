package com.prismprd.prd.chat;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ChatMessage {

    private UUID id;
    private UUID sessionId;
    private String role;
    private String content;
    private String toolName;
    private String toolPayload;
    private OffsetDateTime createdAt;
}
