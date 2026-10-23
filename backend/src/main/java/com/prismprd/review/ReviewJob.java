package com.prismprd.review;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReviewJob {

    private UUID id;
    private UUID prdId;
    private UUID prdVersionId;
    private UUID rubricId;
    private String status;
    private String modelProfile;
    private String errorMessage;
    private UUID createdBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime createdAt;
}
