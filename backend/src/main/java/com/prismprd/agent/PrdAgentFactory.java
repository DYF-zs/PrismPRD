package com.prismprd.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.prd.chat.ChatMessage;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.patch.PrdPatchMapper;
import com.prismprd.prd.patch.PatchStatusWriter;
import com.prismprd.agent.tools.GeneratePatchTool;
import com.prismprd.agent.tools.ReadPrdTool;
import com.prismprd.review.ReviewBlockLinearizer;
import com.prismprd.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PrdAgentFactory {

    private final ChatModelProvider chatModelProvider;
    private final ChatProperties properties;
    private final AgentMessagePersister persister;
    private final PrdDocumentMapper prdDocumentMapper;
    private final PrdPatchMapper patchMapper;
    private final PermissionService permissionService;
    private final PatchStatusWriter patchStatusWriter;
    private final ChatSseService sseService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final com.prismprd.knowledge.KnowledgeService knowledgeService;

    public PrdAgent create(UUID prdId,
                           UUID sessionId,
                           UUID userId,
                           List<ChatMessage> history) {
        permissionService.requireView(prdId, userId);
        PrdDocument document = prdDocumentMapper.selectById(prdId);
        String title = document == null ? null : document.getTitle();
        String outline = document == null ? "" : renderOutline(document);

        AgentContext context = new AgentContext(sessionId, prdId, userId, title, outline);

        // 请求级工具实例：持有本次执行的上下文
        List<ToolCallback> tools = List.of(
                new com.prismprd.agent.tools.KnowledgeSearchTool(context, knowledgeService, objectMapper),
                new ReadPrdTool(context, prdDocumentMapper, objectMapper, permissionService),
                new GeneratePatchTool(context, prdDocumentMapper, patchMapper, permissionService,
                        patchStatusWriter, sseService, objectMapper, transactionTemplate));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(PrdAgentPrompt.systemPrompt(title, outline)));
        messages.addAll(persister.historyToModelMessages(history));

        return new PrdAgent(context, chatModelProvider.chatModel(), tools, messages,
                properties.getMaxSteps(), persister,
                state -> sseService.publish(sessionId, "state", state));
    }

    private String renderOutline(PrdDocument document) {
        try {
            JsonNode draft = objectMapper.readTree(
                    document.getDraftJson() == null ? "{}" : document.getDraftJson());
            List<ReviewBlockLinearizer.BlockEntry> entries = ReviewBlockLinearizer.linearize(draft);
            StringBuilder sb = new StringBuilder();
            for (ReviewBlockLinearizer.BlockEntry entry : entries) {
                if ("heading".equals(entry.type())) {
                    sb.append("- ").append(entry.markdown()).append('\n');
                }
            }
            if (sb.isEmpty() && !entries.isEmpty()) {
                sb.append("（无标题块，共 ").append(entries.size()).append(" 个内容块）");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
