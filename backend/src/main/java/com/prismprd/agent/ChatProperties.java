package com.prismprd.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.chat")
public class ChatProperties {

    private String agent = "configured";

    /** 运行时加载的最近消息条数（ChatMemory 窗口） */
    private int memoryLimit = 20;

    /** Think/Execute 最大循环步数 */
    private int maxSteps = 20;

    /** SSE 连接超时（秒） */
    private long sseTimeoutSeconds = 300;

    /** Agent 线程池大小 */
    private int executorPoolSize = 4;
}
