package com.prismprd.web.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class RubricSummary {

    private UUID id;
    private String rubricKey;
    private String name;
    private Integer version;
    private String description;
    private Integer dimensionCount;
    private OffsetDateTime createdAt;
    private Boolean builtin;
    private Boolean editable;
}
