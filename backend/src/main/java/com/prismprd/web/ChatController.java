package com.prismprd.web;

import com.prismprd.agent.ChatSseService;
import com.prismprd.common.ApiResponse;
import com.prismprd.prd.chat.ChatService;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.ChatMessageDetail;
import com.prismprd.web.dto.ChatSessionSummary;
import com.prismprd.web.dto.CreateSessionRequest;
import com.prismprd.web.dto.SendMessageRequest;
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
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatSseService sseService;
    private final CurrentUserService currentUserService;

    @PostMapping("/api/prds/{prdId}/sessions")
    public ApiResponse<ChatSessionSummary> createSession(@PathVariable UUID prdId,
                                                         @Valid @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        return ApiResponse.ok(chatService.createSession(prdId, title, currentUserService.currentUserId()));
    }

    @GetMapping("/api/prds/{prdId}/sessions")
    public ApiResponse<List<ChatSessionSummary>> listSessions(@PathVariable UUID prdId) {
        return ApiResponse.ok(chatService.listSessions(prdId, currentUserService.currentUserId()));
    }

    @GetMapping("/api/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageDetail>> listMessages(@PathVariable UUID sessionId) {
        return ApiResponse.ok(chatService.listMessages(sessionId, currentUserService.currentUserId()));
    }

    /** 提交用户消息并触发 PrdAgent（异步执行，结果经 SSE 推送） */
    @PostMapping("/api/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessageDetail> sendMessage(@PathVariable UUID sessionId,
                                                     @Valid @RequestBody(required = false) SendMessageRequest request) {
        String content = request == null ? null : request.content();
        return ApiResponse.ok(chatService.sendMessage(sessionId, content, currentUserService.currentUserId()));
    }

    /** SSE：会话消息、Agent 状态与补丁事件 */
    @GetMapping(value = "/api/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID sessionId) {
        // 校验访问权限（会话不存在或无权限时抛出）
        chatService.listMessages(sessionId, currentUserService.currentUserId());
        return sseService.subscribe(sessionId);
    }
}
