package com.prismprd.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferOwnerRequest(@NotNull(message = "userId 不能为空") UUID userId) {
}
