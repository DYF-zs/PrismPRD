package com.prismprd.prd.version;

import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.document.PrdActivityGuard;
import com.prismprd.prd.document.PrdDeletionMapper;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.review.ReviewJobMapper;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionLifecycleService {
    private final PrdVersionMapper versions;
    private final PrdDocumentMapper documents;
    private final VersionLifecycleMapper lifecycle;
    private final PrdDeletionMapper outcomes;
    private final ReviewJobMapper reviews;
    private final PermissionService permissions;
    private final PrdActivityGuard activities;
    private final TransactionTemplate transactions;

    public void useVersion(UUID versionId, long revision, UUID userId) {
        UUID prdId = requireVersion(versionId).getPrdId();
        permissions.requireEdit(prdId, userId);
        try (var lease = activities.exclusive(prdId)) {
            transactions.executeWithoutResult(tx -> {
                lockDocument(prdId);
                permissions.requireEdit(prdId, userId);
                requireVersion(versionId);
                requireIdle(prdId);
                if (lifecycle.useVersion(prdId, versionId, revision) != 1)
                    throw new ConflictException("草稿已被修改，请加载最新内容后重试");
                lifecycle.staleSuggestions(prdId);
                outcomes.refreshReviewOutcome(prdId, versionId);
            });
        }
    }

    public void deleteVersion(UUID versionId, UUID userId) {
        UUID prdId = requireVersion(versionId).getPrdId();
        permissions.requireManage(prdId, userId);
        try (var lease = activities.exclusive(prdId)) {
            transactions.executeWithoutResult(tx -> {
                PrdDocument document = lockDocument(prdId);
                permissions.requireManage(prdId, userId);
                requireVersion(versionId);
                requireIdle(prdId);
                if (versionId.equals(document.getCurrentVersionId())) {
                    if (versions.selectByPrd(prdId).size() > 1)
                        throw new ConflictException("请先使用其他版本，再删除当前基线版本");
                    lifecycle.clearBaseline(prdId);
                }
                lifecycle.deleteSuggestions(versionId);
                lifecycle.deleteReviews(versionId);
                if (lifecycle.deleteVersion(versionId) != 1) throw new NotFoundException("版本不存在");
            });
        }
    }

    private PrdDocument lockDocument(UUID prdId) {
        if (documents.lockById(prdId) == null) throw new NotFoundException("PRD 不存在");
        PrdDocument document = documents.selectById(prdId);
        if (document == null) throw new NotFoundException("PRD 不存在");
        return document;
    }

    private PrdVersion requireVersion(UUID versionId) {
        PrdVersion version = versions.selectById(versionId);
        if (version == null) throw new NotFoundException("版本不存在");
        return version;
    }

    private void requireIdle(UUID prdId) {
        if (reviews.countActive(prdId) > 0) throw new ConflictException("该文档有评审任务正在执行，请等待完成后操作版本");
    }
}
