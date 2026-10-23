package com.prismprd.prd.testcase;

public final class TestCasePrompt {

    private TestCasePrompt() {
    }

    public static String systemPrompt() {
        return """
                你是 PrismPRD 的 TestCase Agent，基于固定的 PRD Version 快照生成结构化测试用例与可测试性分析。

                任务：
                1. 通读带 (blockId) 标注的 PRD 内容，识别可测试的功能需求；
                2. 为每个可测试需求生成测试用例，类型覆盖 FUNCTIONAL（正常路径）、BOUNDARY（边界条件）、EXCEPTION（异常路径）与 SECURITY（权限/安全）；步骤必须可执行，预期结果必须可验证；
                3. 做可测试性分析：找出缺少可验证输入、前置条件、期望结果、异常路径或边界条件的模糊表述（如「响应要快」「操作要简单」），说明问题，并给出可验证的验收标准建议（明确数值、时长、成功率、环境条件）；
                4. sourceBlockId 必须取自内容标注中实际存在的 blockId，sourceQuote 引用对应原文片段。

                输出 JSON 结构：
                {
                  "cases": [
                    {"scenario": "…", "preconditions": "…", "steps": ["…"], "inputData": "…",
                     "expectedResult": "…", "caseType": "FUNCTIONAL|BOUNDARY|EXCEPTION|SECURITY",
                     "sourceBlockId": "…", "sourceQuote": "…"}
                  ],
                  "testabilityIssues": [
                    {"sourceBlockId": "…", "sourceQuote": "…", "issue": "…", "suggestedAcceptanceCriteria": "…"}
                  ],
                  "summary": "用例覆盖分析结论，一到三句话"
                }

                约束：
                - 用例总数不超过 20 条，优先覆盖核心功能需求；
                - steps 为有序步骤数组，每步一个动作；
                - 无可测试需求时 cases 可为空数组，但 testabilityIssues 必须说明原因；
                - 全部输出使用中文。
                """;
    }

    public static String userPrompt(TestCaseContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PRD 标题\n").append(context.prdTitle()).append("\n\n");
        sb.append("# 版本\nv").append(context.versionNo()).append("（固定快照）\n\n");

        if (context.instruction() != null && !context.instruction().isBlank()) {
            sb.append("# 聚焦指令\n").append(context.instruction()).append("\n\n");
        }

        sb.append("# PRD 内容（每行开头括号内为 blockId）\n")
                .append(context.annotatedMarkdown()).append("\n\n");

        sb.append("请按系统要求输出 JSON。");
        return sb.toString();
    }
}
