package com.prismprd.prd.inline;

import com.prismprd.model.ModelClientRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${prism.inline.agent:configured}' != 'mock'")
public class ConfiguredInlineEditAgent implements InlineEditAgent {
    private final ModelClientRegistry registry;

    @Override public InlineEditProposal edit(InlineEditContext context) {
        return registry.current().entity(InlineEditPrompt.systemPrompt(), InlineEditPrompt.userPrompt(context), InlineEditProposal.class);
    }

    @Override public String profile() { return registry.current().profile(); }
}

