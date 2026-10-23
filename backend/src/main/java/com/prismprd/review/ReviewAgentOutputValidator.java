package com.prismprd.review;

import com.prismprd.common.BizException;
import com.prismprd.review.ReviewAgentOutput.DimensionReview;
import com.prismprd.review.ReviewAgentOutput.Evidence;
import com.prismprd.review.ReviewAgentOutput.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReviewAgentOutputValidator {

    public void validate(ReviewAgentOutput output, ReviewContext context) {
        if (output == null) {
            throw new BizException("评审输出为空");
        }
        if (output.getDimensions() == null || output.getDimensions().isEmpty()) {
            throw new BizException("评审输出缺少 dimensions");
        }

        Map<String, ReviewDimension> rubric = context.dimensionsByKey();
        Set<String> seen = new HashSet<>();
        for (DimensionReview dimension : output.getDimensions()) {
            if (dimension == null) throw new BizException("评审输出包含空维度");
            if (dimension.getDimension() == null || !rubric.containsKey(dimension.getDimension())) {
                throw new BizException("评审输出包含未知维度: " + dimension.getDimension());
            }
            if (!seen.add(dimension.getDimension())) {
                throw new BizException("评审输出维度重复: " + dimension.getDimension());
            }
        }
        List<String> missing = rubric.keySet().stream().filter(k -> !seen.contains(k)).toList();
        if (!missing.isEmpty()) {
            throw new BizException("评审输出缺少维度: " + String.join(", ", missing));
        }

        Set<String> blockIds = context.blockIds();
        for (DimensionReview dimension : output.getDimensions()) {
            boolean applicable = !Boolean.FALSE.equals(dimension.getApplicable());
            dimension.setApplicable(applicable);
            if (applicable) {
                if (dimension.getScore() == null) {
                    throw new BizException("维度 " + dimension.getDimension() + " 标记适用但缺少 score");
                }
                if (!Double.isFinite(dimension.getScore()) || dimension.getScore() < 0.0 || dimension.getScore() > 10.0) {
                    throw new BizException("维度 " + dimension.getDimension() + " score 超出 0.0-10.0 范围");
                }
                dimension.setRationale(nvl(dimension.getRationale()));
            } else {
                dimension.setScore(null);
            }
            dimension.setSuggestions(nvlList(dimension.getSuggestions()));
            dimension.setEvidence(nvlEvidence(dimension.getEvidence(), blockIds, context));
        }

        List<Finding> findings = new ArrayList<>();
        if (output.getFindings() != null) {
            for (Finding finding : output.getFindings()) {
                if (finding == null) {
                    continue;
                }
                normalizeFinding(finding, rubric.keySet(), blockIds, context);
                findings.add(finding);
            }
        }
        output.setFindings(findings);

        output.setSummary(nvl(output.getSummary()));
        output.setStrengths(nvlList(output.getStrengths()));
        output.setMajorRisks(nvlList(output.getMajorRisks()));
        if (output.getPriorities() != null) {
            output.setPriorities(new ArrayList<>(output.getPriorities().stream()
                    .filter(p -> p != null && !isBlank(p.getTitle())).toList()));
            output.getPriorities().forEach(p -> {
                p.setLevel(nvl(p.getLevel()));
                p.setReason(nvl(p.getReason()));
            });
        } else {
            output.setPriorities(new ArrayList<>());
        }
    }

    private void normalizeFinding(Finding finding, Set<String> dimensionKeys, Set<String> blockIds, ReviewContext context) {
        String severity;
        try {
            severity = FindingSeverity.parse(finding.getSeverity()).name();
        } catch (Exception e) {
            throw new BizException("Finding severity 非法: " + finding.getSeverity());
        }
        finding.setSeverity(severity);

        List<String> dims = finding.getDimensions() == null ? List.of() : finding.getDimensions();
        for (String dim : dims) {
            if (!dimensionKeys.contains(dim)) {
                throw new BizException("Finding 关联了未知维度: " + dim);
            }
        }
        if (dims.isEmpty()) {
            throw new BizException("Finding 缺少关联维度: " + finding.getTitle());
        }

        if (finding.getLocation() != null) {
            finding.getLocation().setBlockId(normalizeBlockId(finding.getLocation().getBlockId(), blockIds));
            finding.getLocation().setQuote(nvl(finding.getLocation().getQuote()));
        }
        finding.setEvidence(nvlEvidence(finding.getEvidence(), blockIds, context));
        finding.setTitle(nvl(finding.getTitle()));
        finding.setReason(nvl(finding.getReason()));
        finding.setSuggestion(nvl(finding.getSuggestion()));
    }

    private List<Evidence> nvlEvidence(List<Evidence> evidence, Set<String> blockIds, ReviewContext context) {
        if (evidence == null) {
            return new ArrayList<>();
        }
        List<Evidence> result = new ArrayList<>();
        for (Evidence item : evidence) {
            if (item == null) {
                continue;
            }
            if (item.getKnowledgeDocumentId() != null || item.getChunkId() != null ||
                    "KNOWLEDGE_DOCUMENT".equals(item.getSourceType())) {
                var hit = context.getKnowledgeEvidence().stream().filter(h -> h.chunkId().toString().equals(item.getChunkId())
                        && h.knowledgeDocumentId().toString().equals(item.getKnowledgeDocumentId())).findFirst()
                        .orElseThrow(() -> new BizException("评审引用了未检索到的知识来源"));
                if (item.getQuote() == null || item.getQuote().isBlank() || !hit.content().contains(item.getQuote().strip()))
                    throw new BizException("知识引用必须来自对应分块的连续原文");
                item.setSourceType("KNOWLEDGE_DOCUMENT");
                item.setDocumentTitle(hit.documentTitle());
                item.setSectionPath(hit.sectionPath());
                item.setBlockId(null);
            }
            item.setBlockId(normalizeBlockId(item.getBlockId(), blockIds));
            item.setQuote(nvl(item.getQuote()));
            item.setSourceType(nvl(item.getSourceType()));
            result.add(item);
        }
        return result;
    }

    private String normalizeBlockId(String blockId, Set<String> blockIds) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        return blockIds.contains(blockId) ? blockId : null;
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> nvlList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
