package com.prismprd.prd.testcase;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(name = "prism.testcase.agent", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekTestCaseAgent implements TestCaseAgent {

    private final ChatClient chatClient;
    private final String model;

    public DeepSeekTestCaseAgent(DeepSeekChatModel chatModel,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-flash}") String model) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.model = model;
    }

    @Override
    public TestCaseOutput generate(TestCaseContext context) {
        return chatClient.prompt()
                .system(TestCasePrompt.systemPrompt())
                .user(TestCasePrompt.userPrompt(context))
                .options(DeepSeekChatOptions.builder().temperature(0.2).build())
                .call()
                .entity(TestCaseOutput.class);
    }

    @Override
    public String profile() {
        return model;
    }
}
