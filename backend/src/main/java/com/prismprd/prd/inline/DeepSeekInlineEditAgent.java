package com.prismprd.prd.inline;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(name = "prism.inline.agent", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekInlineEditAgent implements InlineEditAgent {

    private final ChatClient chatClient;
    private final String model;

    public DeepSeekInlineEditAgent(DeepSeekChatModel chatModel,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-flash}") String model) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.model = model;
    }

    @Override
    public InlineEditProposal edit(InlineEditContext context) {
        return chatClient.prompt()
                .system(InlineEditPrompt.systemPrompt())
                .user(InlineEditPrompt.userPrompt(context))
                .options(DeepSeekChatOptions.builder().temperature(0.3).build())
                .call()
                .entity(InlineEditProposal.class);
    }

    @Override
    public String profile() {
        return model;
    }
}
