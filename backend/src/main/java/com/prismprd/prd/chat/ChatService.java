package com.prismprd.prd.chat;

import com.prismprd.agent.PrdAgentExecutor;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.ChatMessageDetail;
import com.prismprd.web.dto.ChatSessionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 会话与消息的业务编排：创建/查询会话、历史消息、发送消息并触发 PrdAgent。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PermissionService permissionService;
    private final PrdAgentExecutor agentExecutor;
    private final TransactionTemplate transactionTemplate;

    public ChatSessionSummary createSession(UUID prdId, String title, UUID userId) {
        permissionService.requireEdit(prdId, userId);
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setPrdId(prdId);
        session.setTitle(StringUtils.hasText(title) ? title.trim() : "AI 助手");
        session.setCreatedBy(userId);
        chatSessionMapper.insert(session);
        return toSummary(chatSessionMapper.selectById(session.getId()));
    }

    public List<ChatSessionSummary> listSessions(UUID prdId, UUID userId) {
        permissionService.requireView(prdId, userId);
        return chatSessionMapper.selectByPrd(prdId).stream().map(this::toSummary).toList();
    }

    public List<ChatMessageDetail> listMessages(UUID sessionId, UUID userId) {
        ChatSession session = requireSession(sessionId);
        permissionService.requireView(session.getPrdId(), userId);
        return chatMessageMapper.selectBySession(sessionId).stream().map(this::toDetail).toList();
    }

    /**
     * 提交用户消息并触发 PrdAgent。
     * content 为空时复用会话中最后一条未被回复的 USER 消息（AI_DRAFT 的初始需求）。
     */
    public ChatMessageDetail sendMessage(UUID sessionId, String content, UUID userId) {
        ChatSession session = requireSession(sessionId);
        permissionService.requireView(session.getPrdId(), userId);

        return agentExecutor.submit(sessionId, userId, () -> transactionTemplate.execute(status -> {
            ChatMessage message;
            if (StringUtils.hasText(content)) {
                message = new ChatMessage();
                message.setId(UUID.randomUUID());
                message.setSessionId(sessionId);
                message.setRole("USER");
                message.setContent(content.trim());
                chatMessageMapper.insert(message);
                chatSessionMapper.touchUpdated(sessionId);
            } else {
                message = lastUnansweredUserMessage(sessionId);
                if (message == null) {
                    throw new BizException("消息内容不能为空");
                }
            }

            return toDetail(chatMessageMapper.selectById(message.getId()));
        }));
    }

    /** 最后一条消息是 USER（Agent 尚未回复）时返回它 */
    private ChatMessage lastUnansweredUserMessage(UUID sessionId) {
        List<ChatMessage> recent = chatMessageMapper.selectRecent(sessionId, 1);
        if (recent.size() == 1 && "USER".equals(recent.get(0).getRole())) {
            return recent.get(0);
        }
        return null;
    }

    private ChatSession requireSession(UUID sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        return session;
    }

    private ChatSessionSummary toSummary(ChatSession session) {
        ChatSessionSummary dto = new ChatSessionSummary();
        dto.setId(session.getId());
        dto.setPrdId(session.getPrdId());
        dto.setTitle(session.getTitle());
        dto.setCreatedBy(session.getCreatedBy());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }

    private ChatMessageDetail toDetail(ChatMessage record) {
        ChatMessageDetail dto = new ChatMessageDetail();
        dto.setId(record.getId());
        dto.setSessionId(record.getSessionId());
        dto.setRole(record.getRole());
        dto.setContent(record.getContent());
        dto.setToolName(record.getToolName());
        dto.setToolPayload(record.getToolPayload());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }
}
