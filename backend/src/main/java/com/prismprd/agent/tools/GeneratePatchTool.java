package com.prismprd.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.agent.AgentContext;
import com.prismprd.common.BizException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.patch.PatchApplier;
import com.prismprd.prd.patch.PatchOperation;
import com.prismprd.prd.patch.PatchScope;
import com.prismprd.prd.patch.PatchStatus;
import com.prismprd.prd.patch.PrdPatch;
import com.prismprd.prd.patch.PrdPatchMapper;
import com.prismprd.prd.patch.PatchStatusWriter;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * 生成供用户预览和确认的结构化 PRD 补丁建议。
 * 校验目标块存在后写入 prd_patch（source=AGENT_CHAT，状态 PENDING），
 * 并通过 SSE 推送给前端确认（Accept / Reject）。
 */
@Slf4j
@RequiredArgsConstructor
public class GeneratePatchTool implements ToolCallback {

    public static final String NAME = "generate_patch";
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "scope":{"type":"string","enum":["INLINE","BLOCK"],"description":"INLINE 局部片段，BLOCK 整块"},
              "operation":{"type":"string","enum":["REPLACE","INSERT_BEFORE","INSERT_AFTER","DELETE"]},
              "targetBlockId":{"type":"string","description":"目标块 blockId，必须来自 read_prd 的返回"},
              "sourceQuote":{"type":"string","description":"INLINE 时的原文片段"},
              "newContentMarkdown":{"type":"string","description":"新内容（Markdown），DELETE 时省略"},
              "reason":{"type":"string","description":"生成该修改的原因"}
            },"required":["scope","operation","targetBlockId","reason"]}""";

    private final AgentContext context;
    private final PrdDocumentMapper prdDocumentMapper;
    private final PrdPatchMapper patchMapper;
    private final PermissionService permissionService;
    private final PatchStatusWriter patchStatusWriter;
    private final com.prismprd.agent.ChatSseService sseService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("生成 PRD 修改补丁建议（不直接修改文档）。补丁会在前端展示 Diff，由用户确认接受或拒绝。")
                .inputSchema(SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            JsonNode input = objectMapper.readTree(toolInput == null || toolInput.isBlank() ? "{}" : toolInput);

            // Tool 层强制权限校验（模型 Prompt 中的权限说明仅为语义约束）
            permissionService.requireEdit(context.prdId(), context.userId());

            PrdDocument document = prdDocumentMapper.selectById(context.prdId());
            if (document == null) {
                return "{\"error\":\"PRD 不存在\"}";
            }
            JsonNode draft = objectMapper.readTree(
                    document.getDraftJson() == null ? "{}" : document.getDraftJson());

            PatchScope scope = PatchScope.parse(text(input, "scope", "BLOCK"));
            PatchOperation operation = PatchOperation.parse(text(input, "operation", "REPLACE"));
            String targetBlockId = text(input, "targetBlockId", null);
            if (!PatchApplier.blockExists(draft, targetBlockId)) {
                return "{\"error\":\"targetBlockId 在当前草稿中不存在，请先调用 read_prd 获取有效 blockId\"}";
            }
            String newContent = text(input, "newContentMarkdown", null);
            if (operation.requiresContent() && (newContent == null || newContent.isBlank())) {
                return "{\"error\":\"该操作必须提供 newContentMarkdown\"}";
            }

            PrdPatch patch = new PrdPatch();
            patch.setId(UUID.randomUUID());
            patch.setPrdId(context.prdId());
            patch.setSessionId(context.sessionId());
            patch.setSource("AGENT_CHAT");
            patch.setSourceVersionId(null);
            patch.setBaseDraftRevision(document.getRevision() == null ? 0L : document.getRevision());
            patch.setScope(scope.name());
            patch.setOperation(operation.name());
            patch.setTargetBlockId(targetBlockId);
            patch.setSourceQuote(text(input, "sourceQuote", null));
            patch.setNewContentMarkdown(newContent);
            patch.setReason(text(input, "reason", null));
            patch.setStatus(PatchStatus.PENDING.name());
            patch.setCreatedBy(context.userId());

            PrdPatch saved = transactionTemplate.execute(tx -> {
                patchMapper.insert(patch);
                return patchMapper.selectById(patch.getId());
            });

            // SSE 推送补丁详情，前端展示 Diff 与确认入口
            publishPatchDetail(saved, document);

            var result = objectMapper.createObjectNode();
            result.put("patchId", patch.getId().toString());
            result.put("status", patch.getStatus());
            result.put("message", "补丁已生成，等待用户在前端确认（接受后写入草稿）");
            return objectMapper.writeValueAsString(result);
        } catch (BizException e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.warn("generate_patch 执行失败", e);
            return "{\"error\":\"生成补丁失败：" + e.getMessage() + "\"}";
        }
    }

    private void publishPatchDetail(PrdPatch patch, PrdDocument document) {
        com.prismprd.web.dto.PatchDetail dto = new com.prismprd.web.dto.PatchDetail();
        dto.setId(patch.getId());
        dto.setPrdId(patch.getPrdId());
        dto.setSessionId(patch.getSessionId());
        dto.setCreatedAt(patch.getCreatedAt());
        dto.setSource(patch.getSource());
        dto.setBaseDraftRevision(patch.getBaseDraftRevision());
        dto.setScope(patch.getScope());
        dto.setOperation(patch.getOperation());
        dto.setTargetBlockId(patch.getTargetBlockId());
        dto.setSourceQuote(patch.getSourceQuote());
        dto.setNewContentMarkdown(patch.getNewContentMarkdown());
        dto.setReason(patch.getReason());
        dto.setStatus(patch.getStatus());
        sseService.publish(context.sessionId(), "patch", dto);
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText();
    }
}
