package com.prismprd.prd.testcase;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class TestCase {

    private UUID id;
    private UUID prdVersionId;
    private String scenario;
    private String preconditions;
    /** JSON 数组文本，如 ["步骤一","步骤二"] */
    private String steps;
    private String inputData;
    private String expectedResult;
    /** FUNCTIONAL / BOUNDARY / EXCEPTION / SECURITY */
    private String caseType;
    private String sourceBlockId;
    private OffsetDateTime createdAt;
}
