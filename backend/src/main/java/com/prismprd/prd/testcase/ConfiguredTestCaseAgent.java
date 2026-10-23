package com.prismprd.prd.testcase;

import com.prismprd.model.ModelClientRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${prism.testcase.agent:configured}' != 'mock'")
public class ConfiguredTestCaseAgent implements TestCaseAgent {
    private final ModelClientRegistry registry;

    @Override public TestCaseOutput generate(TestCaseContext context) {
        return registry.current().entity(TestCasePrompt.systemPrompt(), TestCasePrompt.userPrompt(context), TestCaseOutput.class);
    }

    @Override public String profile() { return registry.current().profile(); }
}

