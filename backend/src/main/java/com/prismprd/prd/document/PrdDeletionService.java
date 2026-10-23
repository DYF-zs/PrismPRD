package com.prismprd.prd.document;

import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import com.prismprd.review.ReviewJob;
import com.prismprd.review.ReviewJobMapper;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrdDeletionService {
    private final PrdDocumentMapper documents;
    private final ReviewJobMapper reviews;
    private final PrdDeletionMapper deletion;
    private final PermissionService permissions;
    private final PrdActivityGuard activities;
    private final TransactionTemplate transactions;

    public void deleteDocument(UUID prdId, UUID userId) {
        permissions.requireManage(prdId, userId);
        try (var lease = activities.exclusive(prdId)) {
            transactions.executeWithoutResult(tx -> {
                lockAndAuthorize(prdId, userId);
                requireIdle(prdId);
                if (deletion.deleteDocument(prdId) != 1) throw new NotFoundException("PRD 不存在");
            });
        }
    }

    public void deleteReview(UUID reviewId, UUID userId) {
        ReviewJob visible = requireReview(reviewId);
        UUID prdId = visible.getPrdId();
        permissions.requireManage(prdId, userId);
        try (var lease = activities.exclusive(prdId)) {
            transactions.executeWithoutResult(tx -> {
                lockAndAuthorize(prdId, userId);
                ReviewJob review = requireReview(reviewId);
                requireIdle(prdId);
                deletion.deleteReviewSuggestions(reviewId);
                if (deletion.deleteReview(reviewId) != 1) throw new NotFoundException("评审记录不存在");
                deletion.refreshReviewOutcome(prdId, review.getPrdVersionId());
            });
        }
    }

    private void lockAndAuthorize(UUID prdId, UUID userId) {
        if (documents.lockById(prdId) == null) throw new NotFoundException("PRD 不存在");
        permissions.requireManage(prdId, userId);
    }

    private void requireIdle(UUID prdId) {
        if (reviews.countActive(prdId) > 0) throw new ConflictException("该文档有评审任务正在执行，请等待完成后删除");
    }

    private ReviewJob requireReview(UUID reviewId) {
        ReviewJob review = reviews.selectById(reviewId);
        if (review == null) throw new NotFoundException("评审记录不存在");
        return review;
    }
}
