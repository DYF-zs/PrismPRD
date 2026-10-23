package com.prismprd.prd.inline;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.patch.PatchApplier;
import com.prismprd.prd.patch.PatchOperation;
import com.prismprd.prd.patch.PatchScope;
import com.prismprd.prd.patch.PrdPatch;
import com.prismprd.prd.patch.PrdPatchMapper;
import com.prismprd.common.StalePatchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Inline Edit 生成可审计的待确认 Patch，接受时由 PatchService 校验并更新草稿。
 */
@Service
@RequiredArgsConstructor
public class InlineEditService {

    private final PrdDocumentMapper prdDocumentMapper;
    private final InlineEditAgent inlineEditAgent;
    private final PermissionService permissionService;
    private final PrdPatchMapper patchMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final com.prismprd.common.AiRequestGuard requestGuard;
    private final com.prismprd.prd.document.PrdActivityGuard activities;

    public record Request(@NotBlank @Size(max = 4000) String instruction,
                          @NotBlank @Size(max = 128) String targetBlockId,
                          @Size(max = 50000) String selectionMarkdown,
                          @Size(max = 100000) String blockMarkdown,
                          @Size(max = 200000) String contextMarkdown,
                          @Min(1) @Max(100) Integer attempt,
                          @NotNull @PositiveOrZero Long baseDraftRevision,
                          @Size(max = 50000) String selectionText) {
    }

    public InlineEditResponse generate(UUID prdId, Request request, UUID userId) {
        try (var activity = activities.acquire(prdId); var lease = requestGuard.acquire("inline", prdId)) {
            return generateOnce(prdId, request, userId);
        }
    }

    private InlineEditResponse generateOnce(UUID prdId, Request request, UUID userId) {
        if (request == null || !StringUtils.hasText(request.instruction())) {
            throw new BizException("请输入 AI 指令");
        }
        permissionService.requireEdit(prdId, userId);
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        if (document == null) {
            throw new NotFoundException("PRD 不存在");
        }
        if (request.baseDraftRevision() == null || !Objects.equals(document.getRevision(), request.baseDraftRevision())) {
            throw new StalePatchException("STALE_PATCH：请保存最新草稿后重新生成");
        }
        ObjectNode draft;
        try {
            draft = (ObjectNode) objectMapper.readTree(document.getDraftJson());
        } catch (Exception e) {
            throw new BizException("草稿格式错误");
        }
        if (!PatchApplier.blockExists(draft, request.targetBlockId())) {
            throw new StalePatchException("STALE_PATCH：目标 Block 不存在，请重新生成");
        }

        InlineEditContext context = new InlineEditContext(
                prdId,
                document.getTitle(),
                request.instruction().trim(),
                StringUtils.hasText(request.targetBlockId()) ? request.targetBlockId().trim() : null,
                trimToEmpty(request.selectionMarkdown()),
                trimToEmpty(request.blockMarkdown()),
                trimToEmpty(request.contextMarkdown()),
                request.attempt() == null ? 1 : Math.max(1, request.attempt()));

        InlineEditProposal proposal = inlineEditAgent.edit(context);
        if (proposal == null) throw new BizException("AI 未返回修改建议，请重试");
        if ("INLINE".equals(proposal.getScope())) {
            proposal.setSourceQuote(request.selectionText());
        }
        InlineEditResponse response = toResponse(proposal, context);
        PatchApplier.apply(draft, new PatchApplier.PatchSpec(
                PatchScope.parse(response.getScope()), PatchOperation.parse(response.getOperation()),
                response.getTargetBlockId(), response.getSourceQuote(), response.getNewContentMarkdown()));

        transactionTemplate.executeWithoutResult(tx -> {
            permissionService.requireEdit(prdId, userId);
            prdDocumentMapper.lockById(prdId);
            PrdDocument current = prdDocumentMapper.selectById(prdId);
            if (current == null || !Objects.equals(current.getRevision(), request.baseDraftRevision())) {
                throw new StalePatchException("STALE_PATCH：生成期间草稿已更新，请重新生成");
            }
            PrdPatch patch = new PrdPatch();
            patch.setId(UUID.randomUUID());
            patch.setPrdId(prdId);
            patch.setSource("INLINE_AI");
            patch.setSourceVersionId(document.getCurrentVersionId());
            patch.setBaseDraftRevision(request.baseDraftRevision());
            patch.setScope(response.getScope());
            patch.setOperation(response.getOperation());
            patch.setTargetBlockId(response.getTargetBlockId());
            patch.setSourceQuote(response.getSourceQuote());
            patch.setNewContentMarkdown(response.getNewContentMarkdown());
            patch.setReason(response.getReason() == null ? "" : response.getReason());
            patch.setStatus("PENDING");
            patch.setCreatedBy(userId);
            patchMapper.insert(patch);
            response.setPatchId(patch.getId());
            response.setBaseDraftRevision(patch.getBaseDraftRevision());
        });
        return response;
    }

    private InlineEditResponse toResponse(InlineEditProposal proposal, InlineEditContext context) {
        PatchScope scope = PatchScope.parse(proposal.getScope());
        PatchOperation operation = PatchOperation.parse(proposal.getOperation());
        // 目标 Block 以请求为准，模型输出不采信
        String targetBlockId = context.targetBlockId();

        if (scope == PatchScope.INLINE && !StringUtils.hasText(proposal.getSourceQuote())) {
            throw new BizException("AI 未返回原文定位（sourceQuote），请重试或换一个指令");
        }
        String content = operation.requiresContent() ? proposal.getNewContentMarkdown() : null;
        if (operation.requiresContent() && !StringUtils.hasText(content)) {
            throw new BizException("AI 生成内容为空，请重试");
        }
        if (scope == PatchScope.BLOCK && !StringUtils.hasText(targetBlockId)) {
            throw new BizException("缺少目标 Block 定位，无法应用该修改");
        }

        InlineEditResponse response = new InlineEditResponse();
        response.setScope(scope.name());
        response.setOperation(operation.name());
        response.setTargetBlockId(targetBlockId);
        response.setSourceQuote(trimToNull(proposal.getSourceQuote()));
        response.setNewContentMarkdown(trimToNull(content));
        response.setNewContentJson(convertNodes(scope, content));
        response.setReason(trimToNull(proposal.getReason()));
        response.setProfile(inlineEditAgent.profile());
        return response;
    }

    private ArrayNode convertNodes(PatchScope scope, String content) {
        if (content == null || content.isBlank()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        try {
            if (scope == PatchScope.BLOCK) {
                return PatchApplier.parseBlockNodes(content);
            }
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            PatchApplier.parseInlineNodes(content).forEach(array::add);
            return array;
        } catch (BizException e) {
            throw new BizException("AI 生成内容无法转换：" + e.getMessage() + "，请重试");
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
