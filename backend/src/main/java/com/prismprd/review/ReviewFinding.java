package com.prismprd.review;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReviewFinding {

    private UUID id;
    private UUID reportId;
    private String title;
    private String dimensions;
    private String severity;
    private String blockId;
    private String quote;
    private String evidence;
    private String reason;
    private String suggestion;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
