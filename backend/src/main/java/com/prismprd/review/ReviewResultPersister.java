package com.prismprd.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.review.ReviewAgentOutput.DimensionReview;
import com.prismprd.review.ReviewAgentOutput.Finding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReviewResultPersister {

    private final ReviewReportMapper reportMapper;
    private final ReviewScoreMapper scoreMapper;
    private final ReviewFindingMapper findingMapper;
    private final PrdDocumentMapper prdDocumentMapper;
    private final ScoreCalculator scoreCalculator;
    private final ReadinessCalculator readinessCalculator;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persist(ReviewJob job, ReviewContext context, ReviewAgentOutput output, String modelProfile) {
        BigDecimal overallScore = scoreCalculator.calculate(output.getDimensions());
        Readiness readiness = readinessCalculator.calculate(overallScore, output.getDimensions(), output.getFindings());

        ReviewReport report = new ReviewReport();
        report.setId(UUID.randomUUID());
        report.setReviewJobId(job.getId());
        report.setOverallScore(overallScore);
        report.setReadiness(readiness.name());
        report.setSummary(output.getSummary());
        report.setStrengths(toJson(output.getStrengths()));
        report.setMajorRisks(toJson(output.getMajorRisks()));
        report.setPriorities(toJson(output.getPriorities()));
        report.setModelName(modelProfile);
        report.setModelVersion(modelProfile);
        report.setPromptVersion(ReviewAgentPrompt.VERSION);
        report.setRubricVersion(context.getRubric().getVersion());
        reportMapper.insert(report);

        Map<String, Integer> displayOrder = new HashMap<>();
        for (ReviewDimension dimension : context.getDimensions()) {
            displayOrder.put(dimension.getDimensionKey(), dimension.getDisplayOrder());
        }
        List<ReviewScore> scores = new ArrayList<>();
        for (DimensionReview dimension : output.getDimensions()) {
            ReviewScore score = new ReviewScore();
            score.setId(UUID.randomUUID());
            score.setReportId(report.getId());
            score.setDimensionKey(dimension.getDimension());
            score.setApplicable(dimension.getApplicable());
            score.setScore(dimension.getScore() == null ? null
                    : BigDecimal.valueOf(dimension.getScore()).setScale(1, RoundingMode.HALF_UP));
            score.setRationale(dimension.getApplicable() ? dimension.getRationale()
                    : dimension.getApplicabilityNote());
            score.setEvidence(toJson(dimension.getEvidence()));
            score.setSuggestions(toJson(dimension.getSuggestions()));
            score.setDisplayOrder(displayOrder.getOrDefault(dimension.getDimension(), 0));
            scores.add(score);
        }
        scoreMapper.insertBatch(scores);

        List<ReviewFinding> findings = new ArrayList<>();
        for (Finding finding : output.getFindings()) {
            ReviewFinding row = new ReviewFinding();
            row.setId(UUID.randomUUID());
            row.setReportId(report.getId());
            row.setTitle(truncate(finding.getTitle(), 512));
            row.setDimensions(toJson(finding.getDimensions()));
            row.setSeverity(finding.getSeverity());
            row.setBlockId(finding.getLocation() == null ? null : finding.getLocation().getBlockId());
            row.setQuote(finding.getLocation() == null ? null : finding.getLocation().getQuote());
            row.setEvidence(toJson(finding.getEvidence()));
            row.setReason(finding.getReason());
            row.setSuggestion(finding.getSuggestion());
            row.setStatus(FindingStatus.OPEN.name());
            findings.add(row);
        }
        if (!findings.isEmpty()) {
            findingMapper.insertBatch(findings);
        }

        updatePrdOutcome(job, output, overallScore, readiness);
    }

    private void updatePrdOutcome(ReviewJob job, ReviewAgentOutput output, BigDecimal overallScore, Readiness readiness) {
        PrdDocument prd = prdDocumentMapper.selectById(job.getPrdId());
        if (prd == null) {
            return;
        }
        boolean isCurrentVersion = prd.getCurrentVersionId() != null
                && prd.getCurrentVersionId().equals(job.getPrdVersionId());
        if (!isCurrentVersion) {
            return;
        }
        long blockingCount = output.getFindings().stream()
                .filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity()) || "MAJOR".equalsIgnoreCase(f.getSeverity()))
                .count();
        String nextStatus = blockingCount > 0 ? "NEED_CHANGES"
                : readiness == Readiness.READY ? "APPROVED" : "REVIEWING";
        prdDocumentMapper.updateReviewOutcome(job.getPrdId(), job.getPrdVersionId(), nextStatus, overallScore);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("评审结果序列化失败", e);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
