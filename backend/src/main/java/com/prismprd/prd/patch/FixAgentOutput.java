package com.prismprd.prd.patch;

import lombok.Data;

/**
 * FixAgent 的结构化输出（模型原始输出）。
 * 服务端负责校验 targetBlockId 是否存在于当前草稿。
 */
@Data
public class FixAgentOutput {

    /** INLINE / BLOCK */
    private String scope;

    /** REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE */
    private String operation;

    private String targetBlockId;

    private String sourceQuote;

    private String newContentMarkdown;

    private String reason;
}
