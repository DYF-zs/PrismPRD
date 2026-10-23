package com.prismprd.review;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReviewSseService {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public ReviewSseService(ReviewProperties properties) {
        this.timeoutMillis = properties.getSseTimeoutSeconds() * 1000;
    }

    public SseEmitter subscribe(UUID reviewId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.compute(reviewId, (key, existing) -> {
            List<SseEmitter> list = existing == null ? new CopyOnWriteArrayList<>() : existing;
            if (list.size() >= 8) throw new com.prismprd.common.BizException(429, "该评审的实时连接过多，请关闭多余页面");
            list.add(emitter);
            return list;
        });
        emitter.onCompletion(() -> remove(reviewId, emitter));
        emitter.onTimeout(() -> remove(reviewId, emitter));
        emitter.onError(e -> remove(reviewId, emitter));
        try { emitter.send(SseEmitter.event().name("ready").data("ok")); }
        catch (Exception e) { remove(reviewId, emitter); }
        return emitter;
    }

    public void publish(UUID reviewId, String eventName, Object payload) {
        List<SseEmitter> list = emitters.get(reviewId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                remove(reviewId, emitter);
                try { emitter.complete(); } catch (Exception ignored) { }
            }
        }
    }

    public void heartbeat() {
        emitters.keySet().forEach(id -> publish(id, "heartbeat", "ok"));
    }

    public void complete(UUID reviewId) {
        List<SseEmitter> list = emitters.remove(reviewId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private void remove(UUID reviewId, SseEmitter emitter) {
        emitters.computeIfPresent(reviewId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
