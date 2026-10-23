package com.prismprd.agent;

import java.util.UUID;

/**
 * 一次 Agent 执行的请求级上下文（只读，随 Runtime 构造）。
 */
public record AgentContext(
        UUID sessionId,
        UUID prdId,
        UUID userId,
        String prdTitle,
        String outline) {
}
