package com.prismprd.common;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-instance local AI admission: fail fast instead of accumulating expensive duplicate calls. */
@Component
public class AiRequestGuard {
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Semaphore permits = new Semaphore(4);

    public Lease acquire(String operation, UUID id) {
        String key = operation + ":" + id;
        if (!active.add(key)) throw new BizException(409, "同一目标的 AI 任务正在执行，请勿重复提交");
        if (!permits.tryAcquire()) {
            active.remove(key);
            throw new BizException(503, "AI 任务繁忙，请稍后重试");
        }
        return new Lease(key);
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(String key) { this.key = key; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                active.remove(key);
                permits.release();
            }
        }
    }
}
