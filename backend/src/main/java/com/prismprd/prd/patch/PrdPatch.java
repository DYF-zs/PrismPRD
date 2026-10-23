package com.prismprd.prd.patch;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PrdPatch {

    private UUID id;
    private UUID prdId;
    private UUID sessionId;
    private Long appliedRevision;

    /** INLINE_AI / FINDING_FIX / SECTION */
    private String source;

    private UUID sourceFindingId;
    private UUID sourceVersionId;

    /** 补丁生成时基于的草稿 revision，应用时校验 */
    private Long baseDraftRevision;

    /** INLINE / BLOCK */
    private String scope;

    /** REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE */
    private String operation;

    private String targetBlockId;

    /** 被替换/删除的原文连续片段，用于应用时校验 */
    private String sourceQuote;

    /** 受限 Markdown Fragment，服务端统一解析为 TipTap 节点或 Inline Content */
    private String newContentMarkdown;

    private String reason;

    /** PENDING / APPLIED / REJECTED / STALE */
    private String status;

    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime appliedAt;

    /** 关联 Finding 展示信息（查询时联表填充，不持久化） */
    private String findingTitle;
    private String findingSeverity;
}
