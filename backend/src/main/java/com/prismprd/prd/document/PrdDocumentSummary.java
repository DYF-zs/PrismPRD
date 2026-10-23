package com.prismprd.prd.document;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PrdDocumentSummary {

    private UUID id;
    private String title;
    private String status;
    private Long revision;
    private UUID currentVersionId;
    private Integer currentVersionNo;
    private BigDecimal currentScore;
    private Long reviewCount;
    private UUID ownerId;
    private String ownerName;
    private OffsetDateTime updatedAt;
}
