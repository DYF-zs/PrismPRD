package com.prismprd.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReviewJobSummary {

    private UUID id;
    private UUID prdId;
    private UUID prdVersionId;
    private Integer versionNo;
    private UUID rubricId;
    private String rubricName;
    private Integer rubricVersion;
    private String status;
    private String modelProfile;
    private String errorMessage;
    private BigDecimal overallScore;
    private String readiness;
    private String summary;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
