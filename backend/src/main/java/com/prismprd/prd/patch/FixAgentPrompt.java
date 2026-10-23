package com.prismprd.prd.patch;

public final class FixAgentPrompt {

    public static final String VERSION = "v1";

    private FixAgentPrompt() {
    }

    public static String systemPrompt() {
        return """
                你是 PrismPRD 的 FixAgent，针对单个 Review Finding 生成最小化的结构化修复补丁。

                生成原则：
                - 只修复该 Finding 指出的问题，不重写整篇文档，不引入无关改动；
                - 修改必须落在 Finding 定位的 Block 或其邻近位置，新增内容要与上下文风格一致；
                - 优先用 REPLACE 精确改写问题原文；需要补充缺失信息且无处改写时用 INSERT_AFTER 在目标 Block 后插入；
                - 只有当问题内容整体不可用时才用 DELETE。

                补丁格式要求：
                - scope 取 BLOCK 或 INLINE：BLOCK 用于整块替换或插入（段落、标题、列表等），newContentMarkdown 为 Markdown 片段；INLINE 用于 Block 内局部改写，newContentMarkdown 必须是纯内联 Markdown（禁止标题、列表、代码块）；
                - operation 取 REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE，DELETE 时 newContentMarkdown 为空字符串；
                - targetBlockId 必须来自正文出现过的 blockId，优先使用 Finding 定位的 blockId；
                - scope=INLINE 或 operation=REPLACE/DELETE 时必须提供 sourceQuote，取自目标 Block 原文的连续片段，不得改写；
                - newContentMarkdown 为受限 Markdown Fragment，禁止输出围栏代码块包裹整个内容；
                - reason 简要说明本次修复的思路（一到两句话）。
                """;
    }

    public static String userPrompt(FixContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PRD 标题\n").append(context.prdTitle()).append("\n\n");

        sb.append("# 待修复的 Review Finding\n");
        sb.append("- 标题：").append(context.findingTitle()).append('\n');
        sb.append("- 严重级别：").append(context.findingSeverity()).append('\n');
        if (notBlank(context.findingDimensions())) {
            sb.append("- 关联维度：").append(context.findingDimensions()).append('\n');
        }
        if (notBlank(context.findingBlockId())) {
            sb.append("- 定位 Block：").append(context.findingBlockId()).append('\n');
        }
        if (notBlank(context.findingQuote())) {
            sb.append("- 问题原文：").append(context.findingQuote()).append('\n');
        }
        if (notBlank(context.findingReason())) {
            sb.append("- 原因：").append(context.findingReason()).append('\n');
        }
        if (notBlank(context.findingSuggestion())) {
            sb.append("- 修改建议：").append(context.findingSuggestion()).append('\n');
        }
        if (notBlank(context.findingEvidence())) {
            sb.append("- Evidence：").append(context.findingEvidence()).append('\n');
        }

        sb.append("\n# 当前 PRD 草稿正文（每行格式：(blockId) 内容）\n");
        sb.append(context.annotatedDraft());

        sb.append("\n请按系统要求输出 JSON 修复补丁。");
        return sb.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
