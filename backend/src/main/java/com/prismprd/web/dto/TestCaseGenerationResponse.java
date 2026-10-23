package com.prismprd.web.dto;

import java.util.List;
import java.util.UUID;

public record TestCaseGenerationResponse(
        UUID generationId,
        String profile,
        String summary,
        List<TestCaseDetail> cases,
        List<TestabilityIssueDetail> testabilityIssues,
        List<Integer> appliedIssueIndexes) {
}
