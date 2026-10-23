package com.prismprd.agent;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session 级 SSE 连接管理（多客户端订阅同一会话）。
 * 事件：message（新消息）、patch（Agent 生成的补丁）、state（Agent 状态）、done、error。
 */
@Service
public class ChatSseService {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<UUID, String> states = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public ChatSseService(ChatProperties properties) {
        this.timeoutMillis = properties.getSseTimeoutSeconds() * 1000;
    }

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.compute(sessionId, (key, existing) -> {
            List<SseEmitter> list = existing == null ? new CopyOnWriteArrayList<>() : existing;
            if (list.size() >= 8) throw new com.prismprd.common.BizException(429, "该会话的实时连接过多，请关闭多余页面");
            list.add(emitter);
            return list;
        });
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
            String state = states.getOrDefault(sessionId, "IDLE");
            emitter.send(SseEmitter.event().name("state").data(state, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            remove(sessionId, emitter);
        }
        return emitter;
    }

    public void publish(UUID sessionId, String eventName, Object payload) {
        if ("state".equals(eventName)) states.put(sessionId, String.valueOf(payload));
        if ("done".equals(eventName) || "error".equals(eventName)) states.remove(sessionId);
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                remove(sessionId, emitter);
                try { emitter.complete(); } catch (Exception ignored) { }
            }
        }
    }

    public void heartbeat() {
        emitters.keySet().forEach(id -> publish(id, "heartbeat", "ok"));
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        emitters.computeIfPresent(sessionId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
