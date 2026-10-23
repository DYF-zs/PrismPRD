package com.prismprd.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class FindingDetail {

    private UUID id;
    private String title;
    private List<String> dimensions;
    private String severity;
    private String blockId;
    private String quote;
    private JsonNode evidence;
    private String reason;
    private String suggestion;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
