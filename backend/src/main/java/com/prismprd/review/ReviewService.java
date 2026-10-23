package com.prismprd.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.version.PrdVersion;
import com.prismprd.prd.version.PrdVersionMapper;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.FindingDetail;
import com.prismprd.web.dto.ReportDetail;
import com.prismprd.web.dto.ReviewDetail;
import com.prismprd.web.dto.ReviewJobSummary;
import com.prismprd.web.dto.ScoreDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.common.ConflictException;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewJobMapper reviewJobMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewScoreMapper scoreMapper;
    private final ReviewFindingMapper findingMapper;
    private final ReviewRubricMapper rubricMapper;
    private final ReviewDimensionMapper dimensionMapper;
    private final PrdVersionMapper prdVersionMapper;
    private final PermissionService permissionService;
    private final ReviewExecutionService executionService;
    private final ReviewProperties reviewProperties;
    private final com.prismprd.model.ModelClientRegistry modelRegistry;
    private final ObjectMapper objectMapper;
    private final PrdDocumentMapper documentMapper;

    @Transactional
    public ReviewJobSummary createReview(UUID prdVersionId, UUID rubricId, UUID userId) {
        PrdVersion version = prdVersionMapper.selectById(prdVersionId);
        if (version == null) {
            throw new NotFoundException("PRD 版本不存在");
        }
        permissionService.requireReview(version.getPrdId(), userId);
        if (documentMapper.lockById(version.getPrdId()) == null) throw new NotFoundException("PRD 不存在");
        if (!prdVersionMapper.exists(prdVersionId)) throw new NotFoundException("PRD 版本不存在");
        if (reviewJobMapper.countActive(version.getPrdId()) > 0)
            throw new ConflictException("该 PRD 已有评审执行中，请等待完成后重试");

        ReviewRubric rubric = rubricId != null
                ? requireEnabledRubric(rubricId)
                : defaultRubric();

        ReviewJob job = new ReviewJob();
        job.setId(UUID.randomUUID());
        job.setPrdId(version.getPrdId());
        job.setPrdVersionId(version.getId());
        job.setRubricId(rubric.getId());
        job.setStatus(ReviewJobStatus.PENDING.name());
        job.setModelProfile("mock".equals(reviewProperties.getAgent())
                ? reviewProperties.getModelProfile() : modelRegistry.current().profile());
        job.setCreatedBy(userId);
        reviewJobMapper.insert(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { executionService.submit(job.getId()); }
        });

        return toSummary(job, version.getVersionNo());
    }

    public ReviewDetail getReviewDetail(UUID reviewId, UUID userId) {
        ReviewJob job = requireJob(reviewId);
        permissionService.requireView(job.getPrdId(), userId);
        PrdVersion version = prdVersionMapper.selectById(job.getPrdVersionId());
        ReviewJobSummary summary = toSummary(job, version == null ? null : version.getVersionNo());
        return new ReviewDetail(summary, loadReport(job));
    }

    public List<ReviewJobSummary> listReviews(UUID prdId, UUID userId) {
        permissionService.requireView(prdId, userId);
        return reviewJobMapper.selectByPrd(prdId);
    }

    public ReviewJob requireJob(UUID reviewId) {
        ReviewJob job = reviewJobMapper.selectById(reviewId);
        if (job == null) {
            throw new NotFoundException("评审任务不存在");
        }
        return job;
    }

    private ReportDetail loadReport(ReviewJob job) {
        ReviewReport report = reportMapper.selectByJobId(job.getId());
        if (report == null) {
            return null;
        }
        ReportDetail detail = new ReportDetail();
        detail.setId(report.getId());
        detail.setOverallScore(report.getOverallScore());
        detail.setReadiness(report.getReadiness());
        detail.setSummary(report.getSummary());
        detail.setStrengths(parseStrings(report.getStrengths()));
        detail.setMajorRisks(parseStrings(report.getMajorRisks()));
        detail.setPriorities(parseTree(report.getPriorities()));
        detail.setModelName(report.getModelName());
        detail.setPromptVersion(report.getPromptVersion());
        detail.setRubricVersion(report.getRubricVersion());
        ReviewRubric rubricSnapshot=rubricMapper.selectById(job.getRubricId());
        detail.setRubricId(job.getRubricId());
        detail.setRubricName(rubricSnapshot==null?"未知评审标准":rubricSnapshot.getName());
        detail.setCreatedAt(report.getCreatedAt());

        Map<String, String> dimensionNames = loadDimensionNames(job.getRubricId());
        List<ScoreDetail> scores = new ArrayList<>();
        for (ReviewScore score : scoreMapper.selectByReportId(report.getId())) {
            ScoreDetail dto = new ScoreDetail();
            dto.setDimensionKey(score.getDimensionKey());
            dto.setDimensionName(dimensionNames.getOrDefault(score.getDimensionKey(), score.getDimensionKey()));
            dto.setApplicable(score.getApplicable());
            dto.setScore(score.getScore());
            dto.setRationale(score.getRationale());
            dto.setEvidence(parseTree(score.getEvidence()));
            dto.setSuggestions(parseStrings(score.getSuggestions()));
            dto.setDisplayOrder(score.getDisplayOrder());
            scores.add(dto);
        }
        detail.setScores(scores);

        List<FindingDetail> findings = new ArrayList<>();
        for (ReviewFinding finding : findingMapper.selectByReportId(report.getId())) {
            findings.add(toFindingDetail(finding));
        }
        findings.sort(Comparator.comparingInt(f -> severityRank(f.getSeverity())));
        detail.setFindings(findings);
        return detail;
    }

    public FindingDetail toFindingDetail(ReviewFinding finding) {
        FindingDetail dto = new FindingDetail();
        dto.setId(finding.getId());
        dto.setTitle(finding.getTitle());
        dto.setDimensions(parseStrings(finding.getDimensions()));
        dto.setSeverity(finding.getSeverity());
        dto.setBlockId(finding.getBlockId());
        dto.setQuote(finding.getQuote());
        dto.setEvidence(parseTree(finding.getEvidence()));
        dto.setReason(finding.getReason());
        dto.setSuggestion(finding.getSuggestion());
        dto.setStatus(finding.getStatus());
        dto.setCreatedAt(finding.getCreatedAt());
        dto.setUpdatedAt(finding.getUpdatedAt());
        return dto;
    }

    private Map<String, String> loadDimensionNames(UUID rubricId) {
        Map<String, String> names = new HashMap<>();
        for (ReviewDimension dimension : dimensionMapper.selectByRubric(rubricId)) {
            names.put(dimension.getDimensionKey(), dimension.getName());
        }
        return names;
    }

    private ReviewRubric requireEnabledRubric(UUID rubricId) {
        ReviewRubric rubric = rubricMapper.selectById(rubricId);
        if (rubric == null || !Boolean.TRUE.equals(rubric.getEnabled())) {
            throw new NotFoundException("评审 Rubric 不存在或已停用");
        }
        return rubric;
    }

    private ReviewRubric defaultRubric() {
        ReviewRubric rubric = rubricMapper.selectLatestByKey("default");
        if (rubric == null) {
            throw new BizException("默认评审 Rubric 未初始化");
        }
        return rubric;
    }

    private ReviewJobSummary toSummary(ReviewJob job, Integer versionNo) {
        ReviewJobSummary summary = new ReviewJobSummary();
        summary.setId(job.getId());
        summary.setPrdId(job.getPrdId());
        summary.setPrdVersionId(job.getPrdVersionId());
        summary.setVersionNo(versionNo);
        summary.setRubricId(job.getRubricId());
        ReviewRubric rubricSnapshot=rubricMapper.selectById(job.getRubricId());
        if (rubricSnapshot!=null) {
            summary.setRubricName(rubricSnapshot.getName());
            summary.setRubricVersion(rubricSnapshot.getVersion());
        }
        summary.setStatus(job.getStatus());
        summary.setModelProfile(job.getModelProfile());
        summary.setErrorMessage(job.getErrorMessage());
        summary.setCreatedAt(job.getCreatedAt());
        summary.setStartedAt(job.getStartedAt());
        summary.setFinishedAt(job.getFinishedAt());
        return summary;
    }

    private List<String> parseStrings(String json) {
        try {
            List<String> values = objectMapper.readValue(json == null ? "[]" : json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode parseTree(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
            return node == null ? objectMapper.createArrayNode() : node;
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            case "MINOR" -> 2;
            default -> 3;
        };
    }
}
