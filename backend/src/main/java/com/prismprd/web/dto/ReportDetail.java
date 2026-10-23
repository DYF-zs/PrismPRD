package com.prismprd.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ReportDetail {

    private UUID id;
    private BigDecimal overallScore;
    private String readiness;
    private String summary;
    private List<String> strengths;
    private List<String> majorRisks;
    private JsonNode priorities;
    private String modelName;
    private String promptVersion;
    private Integer rubricVersion;
    private java.util.UUID rubricId;
    private String rubricName;
    private OffsetDateTime createdAt;
    private List<ScoreDetail> scores;
    private List<FindingDetail> findings;
}
