package com.prismprd.web;

import com.prismprd.common.ApiResponse;
import com.prismprd.review.ReviewJob;
import com.prismprd.review.ReviewJobStatus;
import com.prismprd.review.ReviewService;
import com.prismprd.review.ReviewSseService;
import com.prismprd.prd.document.PrdDeletionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.CreateReviewRequest;
import com.prismprd.web.dto.ReviewDetail;
import com.prismprd.web.dto.ReviewJobSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewSseService sseService;
    private final CurrentUserService currentUserService;
    private final PrdDeletionService deletionService;

    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiResponse<Void> delete(@PathVariable UUID reviewId) {
        deletionService.deleteReview(reviewId, currentUserService.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/api/prd-versions/{versionId}/reviews")
    public ApiResponse<ReviewJobSummary> create(@PathVariable UUID versionId,
                                                @Valid @RequestBody(required = false) CreateReviewRequest request) {
        UUID rubricId = request == null ? null : request.getRubricId();
        return ApiResponse.ok(reviewService.createReview(versionId, rubricId, currentUserService.currentUserId()));
    }

    @GetMapping("/api/reviews/{reviewId}")
    public ApiResponse<ReviewDetail> detail(@PathVariable UUID reviewId) {
        return ApiResponse.ok(reviewService.getReviewDetail(reviewId, currentUserService.currentUserId()));
    }

    @GetMapping("/api/prds/{prdId}/reviews")
    public ApiResponse<List<ReviewJobSummary>> list(@PathVariable UUID prdId) {
        return ApiResponse.ok(reviewService.listReviews(prdId, currentUserService.currentUserId()));
    }

    @GetMapping(value = "/api/reviews/{reviewId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID reviewId) {
        UUID userId = currentUserService.currentUserId();
        reviewService.getReviewDetail(reviewId, userId);
        SseEmitter emitter = sseService.subscribe(reviewId);
        ReviewJob job = reviewService.requireJob(reviewId);
        if (ReviewJobStatus.valueOf(job.getStatus()).isTerminal()) {
            sseService.publish(reviewId, "complete",
                    Map.of("reviewId", reviewId.toString(), "status", job.getStatus()));
            emitter.complete();
        }
        return emitter;
    }
}
