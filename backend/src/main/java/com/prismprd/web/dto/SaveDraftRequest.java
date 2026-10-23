package com.prismprd.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveDraftRequest(
        @NotNull(message = "revision 不能为空") @PositiveOrZero Long revision,
        @NotNull(message = "editorJson 不能为空") JsonNode editorJson) {
}
