package com.prismprd.review;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(name = "prism.review.agent", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekReviewAgent implements ReviewAgent {

    private final ChatClient chatClient;
    private final String model;

    public DeepSeekReviewAgent(DeepSeekChatModel chatModel,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-flash}") String model) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.model = model;
    }

    @Override
    public ReviewAgentOutput review(ReviewContext context) {
        return chatClient.prompt()
                .system(ReviewAgentPrompt.systemPrompt())
                .user(ReviewAgentPrompt.userPrompt(context))
                .options(DeepSeekChatOptions.builder().temperature(0.2).build())
                .call()
                .entity(ReviewAgentOutput.class);
    }

    @Override
    public String profile() {
        return model;
    }
}
