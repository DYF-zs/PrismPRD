package com.prismprd.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.agent.AgentContext;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.review.ReviewBlockLinearizer;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 读取当前 PRD 草稿（blockId 标注），供 Agent 决策与定位修改目标。
 * 请求级实例：由 PrdAgentFactory 按会话构造，持有该次执行的上下文。
 */
@Slf4j
@RequiredArgsConstructor
public class ReadPrdTool implements ToolCallback {

    public static final String NAME = "read_prd";
    private static final String SCHEMA = """
            {"type":"object","properties":{},"required":[]}""";

    private final AgentContext context;
    private final PrdDocumentMapper prdDocumentMapper;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("读取当前 PRD 草稿全文。返回标题、revision 与所有文档块（blockId、类型、Markdown 内容）。修改文档前必须先调用。")
                .inputSchema(SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            permissionService.requireView(context.prdId(), context.userId());
            PrdDocument document = prdDocumentMapper.selectById(context.prdId());
            if (document == null) {
                return "{\"error\":\"PRD 不存在\"}";
            }
            JsonNode draft = objectMapper.readTree(
                    document.getDraftJson() == null ? "{}" : document.getDraftJson());
            ObjectNode result = objectMapper.createObjectNode();
            result.put("title", document.getTitle());
            result.put("revision", document.getRevision() == null ? 0 : document.getRevision());
            result.put("status", document.getStatus());
            ArrayNode blocks = result.putArray("blocks");
            for (ReviewBlockLinearizer.BlockEntry entry : ReviewBlockLinearizer.linearize(draft)) {
                ObjectNode block = blocks.addObject();
                block.put("blockId", entry.blockId());
                block.put("type", entry.type());
                block.put("markdown", entry.markdown() == null ? "" : entry.markdown());
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("read_prd 执行失败", e);
            return "{\"error\":\"读取 PRD 失败：" + e.getMessage() + "\"}";
        }
    }
}
