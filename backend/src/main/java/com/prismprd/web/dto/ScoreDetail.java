package com.prismprd.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ScoreDetail {

    private String dimensionKey;
    private String dimensionName;
    private Boolean applicable;
    private BigDecimal score;
    private String rationale;
    private JsonNode evidence;
    private List<String> suggestions;
    private Integer displayOrder;
}
