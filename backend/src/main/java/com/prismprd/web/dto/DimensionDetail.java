package com.prismprd.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class DimensionDetail {

    private String dimensionKey;
    private String name;
    private String description;
    private List<String> checklist;
    private JsonNode scoreAnchor;
    private JsonNode severityAnchor;
    private String applicabilityRule;
    private Integer displayOrder;
}
