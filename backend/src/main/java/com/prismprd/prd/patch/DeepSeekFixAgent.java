package com.prismprd.prd.patch;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "prism.fix.agent", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekFixAgent implements FixAgent {

    private final ChatClient chatClient;
    public DeepSeekFixAgent(DeepSeekChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public FixAgentOutput fix(FixContext context) {
        return chatClient.prompt()
                .system(FixAgentPrompt.systemPrompt())
                .user(FixAgentPrompt.userPrompt(context))
                .options(DeepSeekChatOptions.builder().temperature(0.2).build())
                .call()
                .entity(FixAgentOutput.class);
    }
}
