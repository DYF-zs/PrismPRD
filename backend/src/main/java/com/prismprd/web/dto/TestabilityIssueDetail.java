package com.prismprd.web.dto;

public record TestabilityIssueDetail(
        String sourceBlockId,
        String sourceQuote,
        String issue,
        String suggestedAcceptanceCriteria) {
}
