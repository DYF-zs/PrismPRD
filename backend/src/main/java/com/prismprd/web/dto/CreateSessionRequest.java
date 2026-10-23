package com.prismprd.web.dto;

import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Size(max = 255, message = "标题最长 255 字符") String title) {
}
