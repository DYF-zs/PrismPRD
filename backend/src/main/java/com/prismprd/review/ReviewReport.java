package com.prismprd.review;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReviewReport {

    private UUID id;
    private UUID reviewJobId;
    private BigDecimal overallScore;
    private String readiness;
    private String summary;
    private String strengths;
    private String majorRisks;
    private String priorities;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private Integer rubricVersion;
    private OffsetDateTime createdAt;
}
