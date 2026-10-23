package com.prismprd.review;

import lombok.Data;

import java.util.UUID;

@Data
public class ReviewDimension {

    private UUID id;
    private UUID rubricId;
    private String dimensionKey;
    private String name;
    private String description;
    private String checklist;
    private String scoreAnchor;
    private String severityAnchor;
    private String applicabilityRule;
    private Integer displayOrder;
}
