package com.prismprd.web.dto;

import jakarta.validation.constraints.Size;

public record UpdatePrdRequest(
        @Size(max = 255, message = "标题最长 255 字符") String title,
        @Size(max = 32, message = "状态不合法") String status) {
}
