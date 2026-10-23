package com.prismprd.review;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReviewExecutorConfig {

    @Bean(name = "reviewExecutor")
    public ThreadPoolTaskExecutor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setTaskDecorator(com.prismprd.model.ModelScope::decorate);
        executor.setThreadNamePrefix("review-");
        executor.initialize();
        return executor;
    }
}
