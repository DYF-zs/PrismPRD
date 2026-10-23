package com.prismprd.web.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RubricDetail {

    private UUID id;
    private String rubricKey;
    private String name;
    private Integer version;
    private String description;
    private OffsetDateTime createdAt;
    private List<DimensionDetail> dimensions;
    private Boolean builtin;
    private Boolean editable;
}
