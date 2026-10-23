package com.prismprd.prd.inline;

public final class InlineEditPrompt {

    private InlineEditPrompt() {
    }

    public static String systemPrompt() {
        return """
                你是 PrismPRD 的 Inline Edit Agent，在 PRD 编辑器内对用户选中的局部内容做最小化改写，或按指令在光标处插入新内容。

                生成原则：
                - 严格遵循用户的指令意图（改写、精简、扩写、补充规则、转列表等），不做指令之外的重写；
                - 改写必须保留原文的关键信息与术语，风格与上下文一致；
                - 输出内容必须能直接放入 PRD 正文，符合中文技术文档表达。

                输出格式要求：
                - 用户提供了选中文本（selectionMarkdown 非空）时：scope 用 INLINE，operation 用 REPLACE，sourceQuote 必须原样返回选中文本，newContentMarkdown 为改写后的纯内联 Markdown（禁止标题、列表、代码块）；仅当指令要求删除时用 DELETE 且 newContentMarkdown 为空字符串；
                - 光标插入（selectionMarkdown 为空）时：scope 用 BLOCK，operation 用 INSERT_AFTER，newContentMarkdown 为要插入的 Markdown 片段（段落、列表等完整 Block）；
                - newContentMarkdown 为受限 Markdown Fragment，禁止用围栏代码块包裹整个内容；
                - reason 用一句话说明改写思路。
                """;
    }

    public static String userPrompt(InlineEditContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PRD 标题\n").append(context.prdTitle()).append("\n\n");

        sb.append("# 用户指令\n").append(context.instruction()).append("\n\n");

        if (context.selectionMarkdown() != null && !context.selectionMarkdown().isBlank()) {
            sb.append("# 选中的原文（需改写的目标）\n").append(context.selectionMarkdown()).append("\n\n");
        } else {
            sb.append("# 模式\n光标处插入新内容（无选区）\n\n");
        }

        sb.append("# 选区所在 Block\n");
        sb.append(context.blockMarkdown() == null || context.blockMarkdown().isBlank()
                ? "（空段落）" : context.blockMarkdown()).append("\n\n");

        if (context.contextMarkdown() != null && !context.contextMarkdown().isBlank()) {
            sb.append("# 邻近上下文\n").append(context.contextMarkdown()).append("\n\n");
        }

        if (context.attempt() > 1) {
            sb.append("# 备注\n这是第 ").append(context.attempt())
                    .append(" 次生成，请给出与之前不同的表达。\n\n");
        }

        sb.append("请按系统要求输出 JSON 提案。");
        return sb.toString();
    }
}
