package com.prismprd.agent;

import com.prismprd.common.BizException;
import com.prismprd.prd.chat.ChatMessage;
import com.prismprd.prd.chat.ChatMessageMapper;
import com.prismprd.prd.chat.ChatSession;
import com.prismprd.prd.chat.ChatSessionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 会话级 Agent 异步执行：
 * - 同一个 sessionId 同时只执行一个 Agent 请求（会话级互斥）；
 * - 使用专用线程池异步执行；
 * - 执行结束推送 done 事件，失败推送 error。
 *
 * 请求级预留标识允许 HTTP 线程提交、Agent 线程释放；提交失败也释放预留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrdAgentExecutor {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PrdAgentFactory agentFactory;
    private final ChatSseService sseService;
    private final ChatProperties properties;
    private final com.prismprd.prd.document.PrdActivityGuard activities;

    private final Map<UUID, Object> sessionBusy = new ConcurrentHashMap<>();
    private ThreadPoolTaskExecutor executor;

    @PostConstruct
    public void init() {
        executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("prd-agent-");
        executor.setCorePoolSize(properties.getExecutorPoolSize());
        executor.setMaxPoolSize(properties.getExecutorPoolSize());
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(com.prismprd.model.ModelScope::decorate);
        executor.initialize();
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** 先预留会话与执行队列；消息事务提交成功后，工作线程才读取历史。 */
    public <T> T submit(UUID sessionId, UUID userId, Supplier<T> persistMessage) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) throw new com.prismprd.common.NotFoundException("会话不存在");
        var activity = activities.acquire(session.getPrdId());
        Object reservation = new Object();
        if (sessionBusy.putIfAbsent(sessionId, reservation) != null) {
            activity.close();
            throw new BizException("该会话正在处理上一条消息，请稍候");
        }
        CompletableFuture<Void> committed = new CompletableFuture<>();
        try {
            if (chatSessionMapper.selectById(sessionId) == null) throw new com.prismprd.common.NotFoundException("会话不存在");
            executor.execute(() -> {
                try {
                    committed.join();
                    run(sessionId, userId);
                } catch (java.util.concurrent.CompletionException ignored) {
                    // Agent 执行以消息事务提交成功为前提。
                } finally {
                    sessionBusy.remove(sessionId, reservation);
                    activity.close();
                }
            });
            T result = persistMessage.get();
            sseService.publish(sessionId, "state", "ANALYZING");
            committed.complete(null);
            return result;
        } catch (Exception e) {
            committed.completeExceptionally(e);
            sessionBusy.remove(sessionId, reservation);
            activity.close();
            throw e;
        }
    }

    private void run(UUID sessionId, UUID userId) {
        try {
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                log.warn("会话不存在：{}", sessionId);
                return;
            }
            List<ChatMessage> history = chatMessageMapper.selectRecent(
                    sessionId, properties.getMemoryLimit());
            agentFactory.create(session.getPrdId(), sessionId, userId, history).run();
            sseService.publish(sessionId, "done", "ok");
        } catch (Exception e) {
            log.error("PrdAgent 执行失败 sessionId={}", sessionId, e);
            sseService.publish(sessionId, "error",
                    e.getMessage() == null ? "Agent 执行失败" : e.getMessage());
        }
    }
}
