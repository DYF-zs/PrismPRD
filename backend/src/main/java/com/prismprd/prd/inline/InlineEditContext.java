package com.prismprd.prd.inline;

import java.util.UUID;

/**
 * Inline Edit 的输入上下文：编辑器实时内容（选区 + 目标 Block + 邻近上下文）。
 */
public record InlineEditContext(
        UUID prdId,
        String prdTitle,
        String instruction,
        String targetBlockId,
        /** 用户选中的文本（Markdown），空串表示光标处插入 */
        String selectionMarkdown,
        /** 选区所在 Block 的完整 Markdown */
        String blockMarkdown,
        /** 邻近 Block 的 Markdown 上下文 */
        String contextMarkdown,
        /** 第几次生成（重新生成时递增），供 Agent 变化输出 */
        int attempt) {
}
