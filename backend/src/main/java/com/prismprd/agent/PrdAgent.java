package com.prismprd.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 请求级 Think/Execute 主循环。
 *
 * - think：模型基于会话记忆决策，产生文本或工具调用；
 * - execute：由 ToolCallingManager 手动接管执行（关闭 Spring AI 内部自动执行）；
 * - Assistant / Tool 消息边生成边持久化并经 SSE 推送前端；
 * - 无工具调用或达到 MAX_STEPS 时结束。
 *
 * PrdAgentFactory 为每个请求创建独立的 Agent 实例。
 */
@Slf4j
public class PrdAgent {

    private final AgentContext context;
    private final ChatModel chatModel;
    private final AgentMessagePersister persister;
    private final int maxSteps;
    private final Consumer<String> statePublisher;

    /** 当前会话记忆（含 System，可变追加） */
    private final List<Message> memory;
    private final ChatOptions chatOptions;
    private final ToolCallingManager toolCallingManager;

    private ChatResponse lastChatResponse;

    public PrdAgent(AgentContext context,
                    ChatModel chatModel,
                    List<ToolCallback> tools,
                    List<Message> history,
                    int maxSteps,
                    AgentMessagePersister persister,
                    Consumer<String> statePublisher) {
        this.context = context;
        this.chatModel = chatModel;
        this.persister = persister;
        this.maxSteps = maxSteps;
        this.statePublisher = statePublisher;
        this.memory = new ArrayList<>(history);
        // 关闭 Spring AI 内部的工具调用自动执行，由本类手动接管；
        // 工具回调必须挂到 options 上：executeToolCalls 从 options 解析回调，真实模型也需据此下发工具定义
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(List.copyOf(tools))
                .internalToolExecutionEnabled(false)
                .build();
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    public void run() {
        statePublisher.accept("ANALYZING");
        try {
            for (int step = 1; step <= maxSteps; step++) {
                boolean hasToolCalls = think();
                if (!hasToolCalls) {
                    break;
                }
                statePublisher.accept("MODIFYING");
                execute();
            }
        } catch (Exception e) {
            log.error("PrdAgent 执行失败 sessionId={}", context.sessionId(), e);
            statePublisher.accept("ERROR");
            throw e;
        }
        statePublisher.accept("DONE");
    }

    private boolean think() {
        Prompt prompt = new Prompt(memory, chatOptions);
        this.lastChatResponse = chatModel.call(prompt);
        AssistantMessage output = lastChatResponse.getResult().getOutput();

        persister.persistAssistant(context.sessionId(), output);

        if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
            log.info("[PrdAgent] sessionId={} 工具调用：{}",
                    context.sessionId(),
                    output.getToolCalls().stream().map(AssistantMessage.ToolCall::name).toList());
            return true;
        }
        // 工具轮由 ToolCallingManager 一次性追加 Assistant + Tool，不能提前重复加入。
        memory.add(output);
        return false;
    }

    private void execute() {
        Prompt prompt = new Prompt(memory, chatOptions);
        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, lastChatResponse);

        memory.clear();
        memory.addAll(result.conversationHistory());

        List<Message> history = result.conversationHistory();
        Message last = history.get(history.size() - 1);
        if (last instanceof ToolResponseMessage toolResponseMessage) {
            persister.persistToolResponses(context.sessionId(), toolResponseMessage);
        }
    }
}
