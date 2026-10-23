package com.prismprd.web.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PatchDetail {

    private UUID id;
    private UUID prdId;
    private UUID sessionId;
    private String source;
    private UUID sourceFindingId;
    private UUID sourceVersionId;
    private Long baseDraftRevision;
    private String scope;
    private String operation;
    private String targetBlockId;
    private String sourceQuote;
    private String newContentMarkdown;
    private String reason;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime appliedAt;

    /** 关联 Finding 展示信息 */
    private String findingTitle;
    private String findingSeverity;
}
