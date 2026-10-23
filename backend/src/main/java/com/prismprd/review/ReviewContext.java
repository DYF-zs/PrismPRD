package com.prismprd.review;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class ReviewContext {

    private final UUID prdId;
    private final String prdTitle;
    private final UUID versionId;
    private final Integer versionNo;
    private final ReviewRubric rubric;
    private final List<ReviewDimension> dimensions;
    private final List<ReviewBlockLinearizer.BlockEntry> blocks;
    private final String annotatedContent;
    private List<com.prismprd.knowledge.KnowledgeTypes.Hit> knowledgeEvidence = List.of();

    public Map<String, ReviewDimension> dimensionsByKey() {
        return dimensions.stream().collect(Collectors.toMap(ReviewDimension::getDimensionKey, d -> d));
    }

    public Set<String> blockIds() {
        return blocks.stream().map(ReviewBlockLinearizer.BlockEntry::blockId).collect(Collectors.toSet());
    }
}
