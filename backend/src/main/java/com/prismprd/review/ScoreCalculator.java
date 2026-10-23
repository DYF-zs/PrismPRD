package com.prismprd.review;

import com.prismprd.review.ReviewAgentOutput.DimensionReview;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class ScoreCalculator {

    public BigDecimal calculate(List<DimensionReview> dimensions) {
        List<Double> scores = dimensions.stream()
                .filter(d -> Boolean.TRUE.equals(d.getApplicable()) && d.getScore() != null)
                .map(DimensionReview::getScore)
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double score : scores) {
            sum += score;
        }
        return BigDecimal.valueOf(sum / scores.size()).setScale(1, RoundingMode.HALF_UP);
    }
}
