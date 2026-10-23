package com.prismprd.prd.testcase;

import lombok.Data;

import java.util.List;

/** TestCase Agent 结构化输出：测试用例 + 可测试性分析 + 覆盖结论 */
@Data
public class TestCaseOutput {

    private List<GeneratedCase> cases;

    private List<TestabilityIssue> testabilityIssues;

    private String summary;

    @Data
    public static class GeneratedCase {
        private String scenario;
        private String preconditions;
        private List<String> steps;
        private String inputData;
        private String expectedResult;
        /** FUNCTIONAL / BOUNDARY / EXCEPTION / SECURITY */
        private String caseType;
        private String sourceBlockId;
        private String sourceQuote;
    }

    @Data
    public static class TestabilityIssue {
        private String sourceBlockId;
        private String sourceQuote;
        private String issue;
        private String suggestedAcceptanceCriteria;
    }
}
