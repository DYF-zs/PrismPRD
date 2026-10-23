package com.prismprd.prd.document;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PrdDocument {

    private UUID id;
    private String title;
    private String status;
    private UUID ownerId;
    private String ownerName;
    private String draftJson;
    private String draftMarkdown;
    private Long revision;
    private UUID currentVersionId;
    private Integer currentVersionNo;
    private BigDecimal currentScore;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
