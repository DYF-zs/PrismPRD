package com.prismprd.agent;

import com.prismprd.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatModelProvider {

    private final ObjectProvider<DeepSeekChatModel> deepSeekChatModel;
    private final ChatProperties properties;

    public ChatModel chatModel() {
        return switch (properties.getAgent()) {
            case "deepseek" -> {
                ChatModel model = deepSeekChatModel.getIfAvailable();
                if (model == null) {
                    throw new BizException("DeepSeek 模型不可用，请检查模型服务配置和 DEEPSEEK_API_KEY");
                }
                yield model;
            }
            default -> throw new BizException("不支持的 prism.chat.agent 配置：" + properties.getAgent());
        };
    }
}
