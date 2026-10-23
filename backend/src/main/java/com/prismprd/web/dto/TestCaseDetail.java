package com.prismprd.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TestCaseDetail(
        UUID id,
        String scenario,
        String preconditions,
        List<String> steps,
        String inputData,
        String expectedResult,
        String caseType,
        String sourceBlockId,
        OffsetDateTime createdAt) {
}
