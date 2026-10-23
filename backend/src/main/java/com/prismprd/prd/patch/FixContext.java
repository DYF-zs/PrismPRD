package com.prismprd.prd.patch;

import java.util.UUID;

/**
 * FixAgent 的输入上下文：Finding 信息 + 当前草稿的 blockId 线性化正文。
 */
public record FixContext(
        UUID prdId,
        String prdTitle,
        long draftRevision,
        String annotatedDraft,
        String findingTitle,
        String findingSeverity,
        String findingDimensions,
        String findingBlockId,
        String findingQuote,
        String findingReason,
        String findingSuggestion,
        String findingEvidence) {
}
