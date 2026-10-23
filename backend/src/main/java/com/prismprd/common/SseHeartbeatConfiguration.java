package com.prismprd.common;

import com.prismprd.agent.ChatSseService;
import com.prismprd.review.ReviewSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SseHeartbeatConfiguration {
    private final ChatSseService chat;
    private final ReviewSseService review;

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        chat.heartbeat();
        review.heartbeat();
    }
}
