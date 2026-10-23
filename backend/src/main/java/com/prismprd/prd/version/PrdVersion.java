package com.prismprd.prd.version;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PrdVersion {
    private String titleSnapshot;
    private Long draftRevision;

    private UUID id;
    private UUID prdId;
    private Integer versionNo;
    private String editorJson;
    private String markdownSnapshot;
    private String source;
    private String changeSummary;
    private UUID createdBy;
    private OffsetDateTime createdAt;
}
