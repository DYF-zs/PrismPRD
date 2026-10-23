package com.prismprd.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 提交用户消息。content 为空时，若会话中存在未获得回复的用户消息
 * （如 AI_DRAFT 创建时的 initialRequirement），则直接触发 Agent 处理该消息。
 */
public record SendMessageRequest(
        @Size(max = 8000, message = "消息最长 8000 字符") String content) {
}
