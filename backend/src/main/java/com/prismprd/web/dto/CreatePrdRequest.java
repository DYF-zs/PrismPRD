package com.prismprd.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrdRequest(
        @NotBlank(message = "creationMode 不能为空") String creationMode,
        @NotBlank(message = "标题不能为空") @Size(max = 255, message = "标题最长 255 字符") String title,
        @Size(max = 100) String templateKey,
        @Size(max = 4000, message = "需求描述最长 4000 字符") String initialRequirement) {
}
