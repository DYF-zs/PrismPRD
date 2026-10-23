package com.prismprd.web.dto;

import jakarta.validation.constraints.Size;

public record GenerateTestCasesRequest(@Size(max = 4000, message = "生成要求最长 4000 字符") String instruction) {
}
