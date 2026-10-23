package com.prismprd.review;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewAgentOutput {

    private String summary;
    private List<String> strengths = new ArrayList<>();
    private List<String> majorRisks = new ArrayList<>();
    private List<Priority> priorities = new ArrayList<>();
    private List<DimensionReview> dimensions = new ArrayList<>();
    private List<Finding> findings = new ArrayList<>();

    @Data
    public static class Priority {
        private String level;
        private String title;
        private String reason;
    }

    @Data
    public static class DimensionReview {
        private String dimension;
        private Boolean applicable;
        private Double score;
        private String rationale;
        private List<Evidence> evidence = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private String applicabilityNote;
    }

    @Data
    public static class Evidence {
        private String sourceType;
        private String blockId;
        private String quote;
        private String knowledgeDocumentId;
        private String documentTitle;
        private String chunkId;
        private String sectionPath;
    }

    @Data
    public static class Finding {
        private String title;
        private List<String> dimensions = new ArrayList<>();
        private String severity;
        private Location location;
        private List<Evidence> evidence = new ArrayList<>();
        private String reason;
        private String suggestion;
    }

    @Data
    public static class Location {
        private String blockId;
        private String quote;
    }
}
