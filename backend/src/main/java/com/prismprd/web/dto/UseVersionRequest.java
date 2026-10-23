package com.prismprd.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UseVersionRequest(@NotNull @PositiveOrZero Long revision) {}
