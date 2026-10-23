package com.prismprd.review;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "prism.review.agent", havingValue = "zhipuai")
public class ZhiPuReviewAgent implements ReviewAgent {

    private final ChatClient chatClient;

    public ZhiPuReviewAgent(ZhiPuAiChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public ReviewAgentOutput review(ReviewContext context) {
        return chatClient.prompt()
                .system(ReviewAgentPrompt.systemPrompt())
                .user(ReviewAgentPrompt.userPrompt(context))
                .options(ZhiPuAiChatOptions.builder().temperature(0.2).build())
                .call()
                .entity(ReviewAgentOutput.class);
    }

    @Override
    public String profile() {
        return "glm-4.6";
    }
}
