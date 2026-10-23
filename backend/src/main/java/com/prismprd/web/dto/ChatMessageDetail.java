package com.prismprd.web.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ChatMessageDetail {

    private UUID id;
    private UUID sessionId;
    /** USER / ASSISTANT / TOOL */
    private String role;
    private String content;
    private String toolName;
    private String toolPayload;
    private OffsetDateTime createdAt;
}
