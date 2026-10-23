package com.prismprd.review;

import com.prismprd.review.ReviewAgentOutput.DimensionReview;
import com.prismprd.review.ReviewAgentOutput.Finding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ReadinessCalculator {

    public Readiness calculate(BigDecimal overallScore, List<DimensionReview> dimensions, List<Finding> findings) {
        long criticalCount = findings.stream().filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity())).count();
        long majorCount = findings.stream().filter(f -> "MAJOR".equalsIgnoreCase(f.getSeverity())).count();

        List<Double> applicableScores = dimensions.stream()
                .filter(d -> Boolean.TRUE.equals(d.getApplicable()) && d.getScore() != null)
                .map(DimensionReview::getScore)
                .toList();

        boolean anyLowDimension = applicableScores.stream().anyMatch(s -> s <= 4.0);
        if (criticalCount >= 1 || anyLowDimension) {
            return Readiness.BLOCKED;
        }

        boolean overallAtLeast = overallScore != null && overallScore.compareTo(BigDecimal.valueOf(8.0)) >= 0;
        boolean allDimsHigh = applicableScores.stream().allMatch(s -> s >= 7.0);
        if (criticalCount == 0 && majorCount == 0 && overallAtLeast && allDimsHigh) {
            return Readiness.READY;
        }

        boolean overallOk = overallScore != null && overallScore.compareTo(BigDecimal.valueOf(7.0)) >= 0;
        boolean allDimsOk = applicableScores.stream().allMatch(s -> s >= 5.0);
        if (overallOk && allDimsOk && majorCount <= 2) {
            return Readiness.CONDITIONALLY_READY;
        }
        return Readiness.NEEDS_IMPROVEMENT;
    }
}
