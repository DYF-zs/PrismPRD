package com.prismprd.prd.patch;

import com.prismprd.model.ModelClientRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${prism.fix.agent:configured}' != 'mock'")
public class ConfiguredFixAgent implements FixAgent {
    private final ModelClientRegistry registry;

    @Override public FixAgentOutput fix(FixContext context) {
        return registry.current().entity(FixAgentPrompt.systemPrompt(), FixAgentPrompt.userPrompt(context), FixAgentOutput.class);
    }
}

