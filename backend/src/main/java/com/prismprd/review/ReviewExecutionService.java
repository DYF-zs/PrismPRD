package com.prismprd.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.springframework.core.task.TaskRejectedException;

@Slf4j
@Service
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
public class ReviewExecutionService {

    private final ReviewJobMapper reviewJobMapper;
    private final ReviewContextBuilder contextBuilder;
    private final ReviewAgent reviewAgent;
    private final ReviewAgentOutputValidator validator;
    private final ReviewResultPersister persister;
    private final ReviewSseService sseService;
    private final ThreadPoolTaskExecutor reviewExecutor;

    public ReviewExecutionService(ReviewJobMapper reviewJobMapper,
                                  ReviewContextBuilder contextBuilder,
                                  ReviewAgent reviewAgent,
                                  ReviewAgentOutputValidator validator,
                                  ReviewResultPersister persister,
                                  ReviewSseService sseService,
                                  @Qualifier("reviewExecutor") ThreadPoolTaskExecutor reviewExecutor) {
        this.reviewJobMapper = reviewJobMapper;
        this.contextBuilder = contextBuilder;
        this.reviewAgent = reviewAgent;
        this.validator = validator;
        this.persister = persister;
        this.sseService = sseService;
        this.reviewExecutor = reviewExecutor;
    }

    @PostConstruct
    public void recoverInterrupted() {
        reviewJobMapper.markInterrupted();
    }

    public void submit(UUID reviewJobId) {
        try {
            reviewExecutor.execute(() -> run(reviewJobId));
        } catch (TaskRejectedException e) {
            reviewJobMapper.markFailed(reviewJobId, "评审任务队列已满，请稍后重试");
            sseService.publish(reviewJobId, "error", Map.of("message", "评审任务队列已满，请稍后重试"));
            sseService.complete(reviewJobId);
        }
    }

    private void run(UUID reviewJobId) {
        try {
            if (reviewJobMapper.markRunning(reviewJobId) == 0) {
                return;
            }
            ReviewJob job = reviewJobMapper.selectById(reviewJobId);
            sseService.publish(reviewJobId, "stage", stage("RUNNING", "评审任务开始执行"));

            sseService.publish(reviewJobId, "stage", stage("BUILDING_CONTEXT", "正在构建评审上下文"));
            ReviewContext context = contextBuilder.build(job.getPrdVersionId(), job.getRubricId());
            contextBuilder.enrichKnowledge(context, job.getCreatedBy());

            sseService.publish(reviewJobId, "stage", stage("REVIEWING", "模型正在执行结构化评审"));
            ReviewAgentOutput output = reviewAgent.review(context);

            sseService.publish(reviewJobId, "stage", stage("VALIDATING", "正在校验评审结果"));
            validator.validate(output, context);

            sseService.publish(reviewJobId, "stage", stage("PERSISTING", "正在计算评分并保存结果"));
            persister.persist(job, context, output, reviewAgent.profile());

            reviewJobMapper.markSuccess(reviewJobId);
            sseService.publish(reviewJobId, "complete",
                    Map.of("reviewId", reviewJobId.toString(), "status", "SUCCESS"));
            sseService.complete(reviewJobId);
        } catch (Exception e) {
            log.error("ReviewJob {} 执行失败", reviewJobId, e);
            String message = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
            reviewJobMapper.markFailed(reviewJobId, message.length() > 2000 ? message.substring(0, 2000) : message);
            sseService.publish(reviewJobId, "error",
                    Map.of("reviewId", reviewJobId.toString(), "status", "FAILED", "message", message));
            sseService.complete(reviewJobId);
        }
    }

    private Map<String, String> stage(String stage, String message) {
        return Map.of("stage", stage, "message", message);
    }
}
