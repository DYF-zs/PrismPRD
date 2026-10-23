package com.prismprd.prd.document;

import com.prismprd.common.ConflictException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PrdActivityGuard {
    private final Map<UUID, Integer> active = new HashMap<>();

    public synchronized Lease acquire(UUID prdId) {
        int count = active.getOrDefault(prdId, 0);
        if (count < 0) throw new ConflictException("文档正在执行版本或删除操作，请稍后重试");
        active.put(prdId, count + 1);
        return new Lease(prdId, false);
    }

    public synchronized Lease exclusive(UUID prdId) {
        if (active.containsKey(prdId)) throw new ConflictException("文档有任务或版本操作正在执行，请等待完成后重试");
        active.put(prdId, -1);
        return new Lease(prdId, true);
    }

    public final class Lease implements AutoCloseable {
        private final UUID prdId;
        private final boolean exclusive;
        private boolean closed;

        private Lease(UUID prdId, boolean exclusive) {
            this.prdId = prdId;
            this.exclusive = exclusive;
        }

        @Override
        public void close() {
            synchronized (PrdActivityGuard.this) {
                if (closed) return;
                closed = true;
                int count = active.get(prdId);
                if (exclusive || count == 1) active.remove(prdId);
                else active.put(prdId, count - 1);
            }
        }
    }
}
