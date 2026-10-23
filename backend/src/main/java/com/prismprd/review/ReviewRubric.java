package com.prismprd.review;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReviewRubric {

    private UUID id;
    private String rubricKey;
    private String name;
    private Integer version;
    private String description;
    private Boolean enabled;
    private UUID createdBy;
    private OffsetDateTime createdAt;
}
