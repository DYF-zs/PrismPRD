package com.prismprd.web.dto;

import jakarta.validation.constraints.Size;

public record CreateVersionRequest(@Size(max = 500, message = "版本摘要最长 500 字符") String changeSummary) {

    public CreateVersionRequest {
        if (changeSummary == null || changeSummary.isBlank()) {
            changeSummary = "";
        }
    }
}
