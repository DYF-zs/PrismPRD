package com.prismprd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.prd.chat.ChatMessage;
import com.prismprd.prd.chat.ChatMessageMapper;
import com.prismprd.prd.chat.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PrdAgent 与持久化 / SSE 之间的桥：
 * Assistant 与 Tool 消息边生成边落库，并即时推送给前端。
 * （USER 消息在用户提交时已由 ChatService 持久化，System 不持久化。）
 */
@Component
@RequiredArgsConstructor
public class AgentMessagePersister {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatSseService sseService;
    private final ObjectMapper objectMapper;

    public void persistAssistant(UUID sessionId, AssistantMessage message) {
        ChatMessage record = new ChatMessage();
        record.setId(UUID.randomUUID());
        record.setSessionId(sessionId);
        record.setRole("ASSISTANT");
        record.setContent(message.getText() == null ? "" : message.getText());
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            record.setToolName(message.getToolCalls().size() == 1
                    ? message.getToolCalls().get(0).name() : "tool_calls");
            try {
                record.setToolPayload(objectMapper.writeValueAsString(
                        message.getToolCalls().stream()
                                .map(c -> Map.of("name", c.name(), "arguments", c.arguments()))
                                .toList()));
            } catch (Exception ignored) {
                // 序列化失败不阻塞主流程
            }
        }
        chatMessageMapper.insert(record);
        touchSession(sessionId);
        sseService.publish(sessionId, "message", toDetail(record));
    }

    public void persistToolResponses(UUID sessionId, ToolResponseMessage message) {
        for (ToolResponseMessage.ToolResponse response : message.getResponses()) {
            ChatMessage record = new ChatMessage();
            record.setId(UUID.randomUUID());
            record.setSessionId(sessionId);
            record.setRole("TOOL");
            record.setContent(truncate(response.responseData(), 2000));
            record.setToolName(response.name());
            try {
                record.setToolPayload(objectMapper.writeValueAsString(
                        Map.of("responseData", truncate(response.responseData(), 2000))));
            } catch (Exception ignored) {
                // ignore
            }
            chatMessageMapper.insert(record);
            sseService.publish(sessionId, "message", toDetail(record));
        }
        touchSession(sessionId);
    }

    /** 将持久化会话转换为模型消息，工具结果作为用户消息的附加上下文。 */
    public List<Message> historyToModelMessages(List<ChatMessage> history) {
        return history.stream().<Message>map(record -> switch (record.getRole()) {
            case "USER" -> new org.springframework.ai.chat.messages.UserMessage(record.getContent());
            case "ASSISTANT" -> new AssistantMessage(record.getContent());
            case "TOOL" -> new org.springframework.ai.chat.messages.UserMessage(
                    "[工具 %s 的结果] %s".formatted(record.getToolName(), record.getContent()));
            default -> new org.springframework.ai.chat.messages.UserMessage(record.getContent());
        }).toList();
    }

    public com.prismprd.web.dto.ChatMessageDetail toDetail(ChatMessage record) {
        com.prismprd.web.dto.ChatMessageDetail dto = new com.prismprd.web.dto.ChatMessageDetail();
        dto.setId(record.getId());
        dto.setSessionId(record.getSessionId());
        dto.setRole(record.getRole());
        dto.setContent(record.getContent());
        dto.setToolName(record.getToolName());
        dto.setToolPayload(record.getToolPayload());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }

    private void touchSession(UUID sessionId) {
        chatSessionMapper.touchUpdated(sessionId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…（截断）";
    }
}
