package com.prismprd.review;

import com.prismprd.model.ModelClientRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${prism.review.agent:configured}' != 'mock'")
public class ConfiguredReviewAgent implements ReviewAgent {
    private final ModelClientRegistry registry;

    @Override public ReviewAgentOutput review(ReviewContext context) {
        return registry.current().entity(ReviewAgentPrompt.systemPrompt(), ReviewAgentPrompt.userPrompt(context), ReviewAgentOutput.class);
    }

    @Override public String profile() { return registry.current().profile(); }
}

