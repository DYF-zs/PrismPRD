package com.prismprd.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ImportRequest(
        @NotNull(message = "revision 不能为空") @PositiveOrZero Long revision,
        @NotBlank(message = "markdown 不能为空") @Size(max = 500000) String markdown) {
}
