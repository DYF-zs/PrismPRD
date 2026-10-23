package com.prismprd.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import com.prismprd.model.ModelClientRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatModelProvider {

    private final ModelClientRegistry registry;

    public ChatModel chatModel() {
        return registry.current().model();
    }
}
