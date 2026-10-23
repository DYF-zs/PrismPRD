package com.prismprd.prd.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.common.StalePatchException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.document.editor.TiptapDocNormalizer;
import com.prismprd.prd.document.editor.TiptapMarkdownSerializer;
import com.prismprd.review.ReviewBlockLinearizer;
import com.prismprd.review.ReviewFinding;
import com.prismprd.review.ReviewFindingMapper;
import com.prismprd.review.ReviewJob;
import com.prismprd.review.ReviewJobMapper;
import com.prismprd.review.ReviewReport;
import com.prismprd.review.ReviewReportMapper;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.PatchDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatchService {

    private final ReviewFindingMapper findingMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewJobMapper reviewJobMapper;
    private final PrdDocumentMapper prdDocumentMapper;
    private final PrdPatchMapper patchMapper;
    private final FixAgent fixAgent;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final PatchStatusWriter patchStatusWriter;
    private final TransactionTemplate transactionTemplate;
    private final com.prismprd.common.AiRequestGuard requestGuard;
    private final com.prismprd.prd.document.PrdActivityGuard activities;

    // ==================== Finding -> 生成 AI Fix Patch ====================

    public PatchDetail createFix(UUID findingId, UUID userId) {
        try (var lease = requestGuard.acquire("fix", findingId)) {
            return createFixOnce(findingId, userId);
        }
    }

    private PatchDetail createFixOnce(UUID findingId, UUID userId) {
        ReviewFinding finding = findingMapper.selectById(findingId);
        if (finding == null) {
            throw new NotFoundException("Finding 不存在");
        }
        ReviewReport report = reportMapper.selectById(finding.getReportId());
        ReviewJob job = report == null ? null : reviewJobMapper.selectById(report.getReviewJobId());
        if (job == null) {
            throw new NotFoundException("评审任务不存在");
        }
        try (var activity = activities.acquire(job.getPrdId())) {
            return generateFix(finding, job, userId);
        }
    }

    private PatchDetail generateFix(ReviewFinding finding, ReviewJob job, UUID userId) {
        UUID findingId = finding.getId();
        permissionService.requireEdit(job.getPrdId(), userId);
        if (findingMapper.selectById(findingId) == null) throw new NotFoundException("Finding 不存在");

        PrdDocument document = requireDocument(job.getPrdId());
        JsonNode draftDoc = readTree(document.getDraftJson());

        FixContext context = new FixContext(
                job.getPrdId(),
                document.getTitle(),
                document.getRevision(),
                ReviewBlockLinearizer.renderAnnotated(ReviewBlockLinearizer.linearize(draftDoc)),
                finding.getTitle(),
                finding.getSeverity(),
                finding.getDimensions(),
                finding.getBlockId(),
                finding.getQuote(),
                finding.getReason(),
                finding.getSuggestion(),
                finding.getEvidence());

        FixAgentOutput output = fixAgent.fix(context);
        if (output == null) throw new BizException("AI 未返回修复建议，请重试");

        // 确定性后端：模型给出的目标 Block 必须存在于当前草稿，否则回退到 Finding 定位
        String targetBlockId = normalizeBlockId(output.getTargetBlockId(), draftDoc, finding.getBlockId());
        PatchScope scope = PatchScope.parse(output.getScope());
        PatchOperation operation = PatchOperation.parse(output.getOperation());
        if (operation == PatchOperation.REPLACE && scope == PatchScope.INLINE && !StringUtils.hasText(output.getSourceQuote())) {
            output.setSourceQuote(finding.getQuote());
        }

        // Validate generated content before superseding a usable pending suggestion.
        TiptapDocNormalizer.normalize(PatchApplier.apply((ObjectNode) draftDoc,
                new PatchApplier.PatchSpec(scope, operation, targetBlockId,
                        trimToNull(output.getSourceQuote()), output.getNewContentMarkdown())));

        return transactionTemplate.execute(tx -> {
        permissionService.requireEdit(job.getPrdId(), userId);
        prdDocumentMapper.lockById(job.getPrdId());
        if (!document.getRevision().equals(requireDocument(job.getPrdId()).getRevision()))
            throw new StalePatchException("生成期间草稿已变化，请重新生成修复建议");
        // 重新生成视为替换：作废该 Finding 已有的待处理补丁
        for (PrdPatch pending : patchMapper.selectPendingByFinding(findingId)) {
            patchMapper.updateStatus(pending.getId(), PatchStatus.REJECTED.name());
        }

        PrdPatch patch = new PrdPatch();
        patch.setId(UUID.randomUUID());
        patch.setPrdId(job.getPrdId());
        patch.setSource("FINDING_FIX");
        patch.setSourceFindingId(findingId);
        patch.setSourceVersionId(job.getPrdVersionId());
        patch.setBaseDraftRevision(document.getRevision());
        patch.setScope(scope.name());
        patch.setOperation(operation.name());
        patch.setTargetBlockId(targetBlockId);
        patch.setSourceQuote(trimToNull(output.getSourceQuote()));
        patch.setNewContentMarkdown(trimToNull(operation.requiresContent() ? output.getNewContentMarkdown() : null));
        patch.setReason(trimToNull(output.getReason()));
        patch.setStatus(PatchStatus.PENDING.name());
        patch.setCreatedBy(userId);
        // findingTitle/findingSeverity 为联表字段（不入 INSERT 列），此处从 finding 补齐以便返回完整详情
        patch.setFindingTitle(finding.getTitle());
        patch.setFindingSeverity(finding.getSeverity());
        patchMapper.insert(patch);
        return toDetail(patchMapper.selectById(patch.getId()));
        });
    }

    // ==================== 应用 / 拒绝 ====================

    public PatchDetail applyPatch(UUID patchId, UUID userId) {
        try {
            return transactionTemplate.execute(tx -> applyInTransaction(patchId, userId));
        } catch (StalePatchException e) {
            // 先释放补丁行锁并回滚正文事务，再记录过期状态。
            patchStatusWriter.markStale(patchId);
            throw e;
        }
    }

    private PatchDetail applyInTransaction(UUID patchId, UUID userId) {
        PrdPatch visible = requirePatch(patchId);
        permissionService.requireEdit(visible.getPrdId(), userId);
        prdDocumentMapper.lockById(visible.getPrdId());
        PrdPatch patch = patchMapper.selectByIdForUpdate(patchId);
        if (patch == null) throw new NotFoundException("补丁不存在");
        if (!PatchStatus.PENDING.name().equals(patch.getStatus())) {
            throw new BizException("补丁当前状态为 " + patch.getStatus() + "，只有待处理补丁可以应用");
        }
        permissionService.requireEdit(patch.getPrdId(), userId);

        PrdDocument document = requireDocument(patch.getPrdId());
        if (document.getRevision() == null
                || document.getRevision().longValue() != patch.getBaseDraftRevision()) {
            throw new StalePatchException("STALE_PATCH：草稿已更新，补丁基于的版本过期，请重新生成修复建议");
        }

        ObjectNode draftDoc;
        try {
            draftDoc = (ObjectNode) readTree(document.getDraftJson());
        } catch (ClassCastException e) {
            throw new BizException("草稿内容格式错误");
        }
        ObjectNode updated = PatchApplier.apply(draftDoc, new PatchApplier.PatchSpec(
                PatchScope.parse(patch.getScope()),
                PatchOperation.parse(patch.getOperation()),
                patch.getTargetBlockId(),
                patch.getSourceQuote(),
                patch.getNewContentMarkdown()));
        ObjectNode normalized = TiptapDocNormalizer.normalize(updated);
        String markdown = TiptapMarkdownSerializer.serialize(normalized);

        int rows = prdDocumentMapper.updateDraft(patch.getPrdId(),
                patch.getBaseDraftRevision(), normalized.toString(), markdown);
        if (rows == 0) {
            throw new StalePatchException("STALE_PATCH：草稿发生并发修改，请重新生成修复建议");
        }

        patchMapper.updateStatus(patchId, PatchStatus.APPLIED.name());
        if (patch.getSourceFindingId() != null) {
            findingMapper.updateStatus(patch.getSourceFindingId(), "RESOLVED");
        }
        return toDetail(patchMapper.selectById(patchId));
    }

    @Transactional
    public PatchDetail rejectPatch(UUID patchId, UUID userId) {
        PrdPatch visible = requirePatch(patchId);
        permissionService.requireEdit(visible.getPrdId(), userId);
        prdDocumentMapper.lockById(visible.getPrdId());
        PrdPatch patch = patchMapper.selectByIdForUpdate(patchId);
        if (patch == null) throw new NotFoundException("补丁不存在");
        if (!PatchStatus.PENDING.name().equals(patch.getStatus())) {
            throw new BizException("补丁当前状态为 " + patch.getStatus() + "，只有待处理补丁可以拒绝");
        }
        permissionService.requireEdit(patch.getPrdId(), userId);
        patchMapper.updateStatus(patchId, PatchStatus.REJECTED.name());
        return toDetail(patchMapper.selectById(patchId));
    }

    public List<PatchDetail> listByPrd(UUID prdId, UUID userId) {
        permissionService.requireView(prdId, userId);
        return patchMapper.selectByPrd(prdId).stream().map(PatchService::toDetail).toList();
    }

    // ==================== 内部工具 ====================

    private PrdDocument requireDocument(UUID prdId) {
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        if (document == null) {
            throw new NotFoundException("PRD 不存在");
        }
        return document;
    }

    private PrdPatch requirePatch(UUID patchId) {
        PrdPatch patch = patchMapper.selectById(patchId);
        if (patch == null) {
            throw new NotFoundException("补丁不存在");
        }
        return patch;
    }

    private String normalizeBlockId(String candidate, JsonNode draftDoc, String findingBlockId) {
        if (PatchApplier.blockExists(draftDoc, candidate)) {
            return candidate;
        }
        if (PatchApplier.blockExists(draftDoc, findingBlockId)) {
            return findingBlockId;
        }
        throw new BizException("AI 生成的修复目标在当前草稿中不存在，请重试或补充 Finding 定位");
    }

    public static PatchDetail toDetail(PrdPatch patch) {
        PatchDetail dto = new PatchDetail();
        dto.setId(patch.getId());
        dto.setPrdId(patch.getPrdId());
        dto.setSessionId(patch.getSessionId());
        dto.setSource(patch.getSource());
        dto.setSourceFindingId(patch.getSourceFindingId());
        dto.setSourceVersionId(patch.getSourceVersionId());
        dto.setBaseDraftRevision(patch.getBaseDraftRevision());
        dto.setScope(patch.getScope());
        dto.setOperation(patch.getOperation());
        dto.setTargetBlockId(patch.getTargetBlockId());
        dto.setSourceQuote(patch.getSourceQuote());
        dto.setNewContentMarkdown(patch.getNewContentMarkdown());
        dto.setReason(patch.getReason());
        dto.setStatus(patch.getStatus());
        dto.setCreatedAt(patch.getCreatedAt());
        dto.setAppliedAt(patch.getAppliedAt());
        dto.setFindingTitle(patch.getFindingTitle());
        dto.setFindingSeverity(patch.getFindingSeverity());
        return dto;
    }

    private JsonNode readTree(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
            if (!node.isObject()) {
                throw new BizException("草稿内容格式错误");
            }
            return node;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("草稿内容解析失败");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
