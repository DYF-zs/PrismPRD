package com.prismprd.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublishRubricRequest(
        @NotBlank @Size(max=128) String name,
        @Size(max=4000) String description,
        UUID baseRubricId,
        @NotEmpty @Size(max=24) List<@NotNull @Valid Dimension> dimensions) {
    public record Dimension(
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String dimensionKey,
            @NotBlank @Size(max=128) String name,
            @Size(max=4000) String description,
            @NotEmpty @Size(max=50) List<@NotBlank @Size(max=500) String> checklist,
            @NotEmpty @Size(max=20) List<@NotNull @Valid ScoreBand> scoreAnchor,
            @NotNull @Size(min=4,max=4) Map<@Pattern(regexp="CRITICAL|MAJOR|MINOR|SUGGESTION") String,
                    @NotBlank @Size(max=1000) String> severityAnchor,
            @Size(max=4000) String applicabilityRule) {}
    public record ScoreBand(
            @NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer=2,fraction=1) BigDecimal min,
            @NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer=2,fraction=1) BigDecimal max,
            @NotBlank @Size(max=1000) String description) {}
}
