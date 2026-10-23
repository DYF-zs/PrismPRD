package com.prismprd.prd.testcase;

public interface TestCaseAgent {

    TestCaseOutput generate(TestCaseContext context);

    String profile();
}
