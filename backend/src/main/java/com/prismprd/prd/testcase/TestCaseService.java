package com.prismprd.prd.testcase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.version.PrdVersion;
import com.prismprd.prd.version.PrdVersionMapper;
import com.prismprd.review.ReviewBlockLinearizer;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.GenerateTestCasesRequest;
import com.prismprd.web.dto.TestCaseDetail;
import com.prismprd.web.dto.TestCaseGenerationResponse;
import com.prismprd.web.dto.TestabilityIssueDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.prismprd.common.StalePatchException;
import com.prismprd.prd.patch.*;
import com.prismprd.web.dto.PatchDetail;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 测试用例生成编排：基于固定 PRD Version 快照同步生成，生成结果替换该版本已有用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private static final Set<String> CASE_TYPES = Set.of("FUNCTIONAL", "BOUNDARY", "EXCEPTION", "SECURITY");
    private static final int MAX_CASES = 20;
    private static final int MAX_ISSUES = 20;

    private final PrdVersionMapper prdVersionMapper;
    private final PrdDocumentMapper prdDocumentMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestCaseAgent testCaseAgent;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PrdPatchMapper patchMapper;
    private final com.prismprd.common.AiRequestGuard requestGuard;
    private final com.prismprd.prd.document.PrdActivityGuard activities;

    public TestCaseGenerationResponse generate(UUID versionId, GenerateTestCasesRequest request, UUID userId) {
        PrdVersion version = requireVersion(versionId, userId);
        try (var activity = activities.acquire(version.getPrdId()); var lease = requestGuard.acquire("testcase", versionId)) {
            return generateOnce(versionId, request, userId);
        }
    }

    private TestCaseGenerationResponse generateOnce(UUID versionId, GenerateTestCasesRequest request, UUID userId) {
        PrdVersion version = prdVersionMapper.selectById(versionId);
        if (version == null) {
            throw new NotFoundException("PRD 版本不存在");
        }
        permissionService.requireEdit(version.getPrdId(), userId);
        PrdDocument prd = prdDocumentMapper.selectById(version.getPrdId());
        if (prd == null) {
            throw new NotFoundException("PRD 不存在");
        }

        List<ReviewBlockLinearizer.BlockEntry> blocks = linearize(version);
        if (blocks.isEmpty()) {
            throw new BizException("版本内容为空，无法生成测试用例");
        }
        Set<String> blockIds = new HashSet<>();
        blocks.forEach(b -> blockIds.add(b.blockId()));

        String instruction = request == null || !StringUtils.hasText(request.instruction())
                ? null : request.instruction().trim();
        TestCaseContext context = new TestCaseContext(prd.getId(), version.getTitleSnapshot(), version.getVersionNo(),
                instruction, blocks, ReviewBlockLinearizer.renderAnnotated(blocks));

        TestCaseOutput output = testCaseAgent.generate(context);
        if (output == null) throw new BizException("AI 未返回测试分析，请重试");

        List<TestCaseDetail> details = new ArrayList<>();
        List<TestCase> entities = new ArrayList<>();
        if (output.getCases() != null) {
            for (TestCaseOutput.GeneratedCase c : output.getCases()) {
                if (entities.size() >= MAX_CASES) {
                    break;
                }
                if (c == null || !StringUtils.hasText(c.getScenario()) || !StringUtils.hasText(c.getExpectedResult())) {
                    continue;
                }
                if (c.getSourceBlockId() == null || !blockIds.contains(c.getSourceBlockId())) {
                    continue;
                }
                List<String> steps = cleanSteps(c.getSteps());
                if (steps.isEmpty()) {
                    continue;
                }
                String caseType = StringUtils.hasText(c.getCaseType())
                        && CASE_TYPES.contains(c.getCaseType().trim().toUpperCase())
                        ? c.getCaseType().trim().toUpperCase() : "FUNCTIONAL";

                TestCase entity = new TestCase();
                entity.setId(UUID.randomUUID());
                entity.setPrdVersionId(versionId);
                entity.setScenario(c.getScenario().trim());
                entity.setPreconditions(trimToEmpty(c.getPreconditions()));
                entity.setSteps(toStepsJson(steps));
                entity.setInputData(trimToNull(c.getInputData()));
                entity.setExpectedResult(c.getExpectedResult().trim());
                entity.setCaseType(caseType);
                entity.setSourceBlockId(c.getSourceBlockId());
                entities.add(entity);
                details.add(toDetail(entity, steps));
            }
        }

        List<TestabilityIssueDetail> issues = new ArrayList<>();
        if (output.getTestabilityIssues() != null) {
            for (TestCaseOutput.TestabilityIssue issue : output.getTestabilityIssues()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                if (issue == null || !StringUtils.hasText(issue.getIssue())) {
                    continue;
                }
                if (issue.getSourceBlockId() == null || !blockIds.contains(issue.getSourceBlockId())) {
                    continue;
                }
                issues.add(new TestabilityIssueDetail(
                        issue.getSourceBlockId(),
                        trimToNull(issue.getSourceQuote()),
                        issue.getIssue().trim(),
                        trimToNull(issue.getSuggestedAcceptanceCriteria())));
            }
        }

        if (details.isEmpty() && issues.isEmpty()) {
            throw new BizException("AI 未生成有效的测试用例，请重试");
        }

        log.info("generated {} test cases and {} testability issues for version {} (profile={})",
                details.size(), issues.size(), versionId, testCaseAgent.profile());

        return transactionTemplate.execute(tx -> {
            permissionService.requireEdit(version.getPrdId(), userId);
            prdDocumentMapper.lockById(version.getPrdId());
            testCaseMapper.deleteByVersion(versionId);
            if (!entities.isEmpty()) testCaseMapper.insertBatch(entities);
            var result = new TestCaseGenerationResponse(UUID.randomUUID(), testCaseAgent.profile(),
                    trimToNull(output.getSummary()), list(versionId, userId), issues, List.of());
            testCaseMapper.saveReport(versionId, toJson(result));
            return result;
        });
    }

    public TestCaseGenerationResponse report(UUID versionId, UUID userId) {
        PrdVersion version = requireVersion(versionId, userId);
        String json = testCaseMapper.selectReport(version.getId());
        if (json == null) return new TestCaseGenerationResponse(null, null, null, list(versionId, userId), List.of(), List.of());
        try {
            var saved = objectMapper.readValue(json, TestCaseGenerationResponse.class);
            return new TestCaseGenerationResponse(saved.generationId(), saved.profile(), saved.summary(),
                    saved.cases(), saved.testabilityIssues(), testCaseMapper.selectAppliedIssues(saved.generationId()));
        }
        catch (Exception e) { throw new BizException("测试生成报告读取失败"); }
    }

    public record AcceptanceRequest(@jakarta.validation.constraints.NotNull UUID generationId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Integer issueIndex,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long baseDraftRevision) {}

    /** 仅将持久化报告中的建议转换为待确认 Patch；此方法不改写正文。 */
    @Transactional
    public PatchDetail proposeAcceptance(UUID versionId, AcceptanceRequest request, UUID userId) {
        PrdVersion version = requireVersion(versionId, userId);
        permissionService.requireEdit(version.getPrdId(), userId);
        prdDocumentMapper.lockById(version.getPrdId());
        var report = report(versionId, userId);
        if (request == null || request.generationId() == null || !request.generationId().equals(report.generationId()))
            throw new StalePatchException("测试报告已更新，请重新加载建议");
        if (request.issueIndex() < 0 || request.issueIndex() >= report.testabilityIssues().size())
            throw new BizException("验收建议不存在");
        UUID existing = testCaseMapper.selectAcceptancePatch(request.generationId(), request.issueIndex());
        if (existing != null) return PatchService.toDetail(patchMapper.selectById(existing));
        var issue = report.testabilityIssues().get(request.issueIndex());
        if (!StringUtils.hasText(issue.suggestedAcceptanceCriteria())) throw new BizException("该问题没有验收标准建议");
        PrdDocument document = prdDocumentMapper.selectById(version.getPrdId());
        if (request.baseDraftRevision() == null || !request.baseDraftRevision().equals(document.getRevision()))
            throw new StalePatchException("草稿已变化，请保存最新内容后重试");
        try {
            JsonNode original = findBlock(objectMapper.readTree(version.getEditorJson()), issue.sourceBlockId());
            ObjectNode current = (ObjectNode) objectMapper.readTree(document.getDraftJson());
            JsonNode target = findBlock(current, issue.sourceBlockId());
            if (original == null || !original.equals(target))
                throw new StalePatchException("来源章节已变化，请创建新版本并重新生成测试分析");
            String markdown = "### 验收标准\n\n" + issue.suggestedAcceptanceCriteria();
            PatchApplier.apply(current, new PatchApplier.PatchSpec(PatchScope.BLOCK,
                    PatchOperation.INSERT_AFTER, issue.sourceBlockId(), null, markdown));
            PrdPatch patch = new PrdPatch();
            patch.setId(UUID.randomUUID());
            patch.setPrdId(version.getPrdId());
            patch.setSource("ACCEPTANCE");
            patch.setSourceVersionId(versionId);
            patch.setBaseDraftRevision(document.getRevision());
            patch.setScope("BLOCK");
            patch.setOperation("INSERT_AFTER");
            patch.setTargetBlockId(issue.sourceBlockId());
            patch.setNewContentMarkdown(markdown);
            patch.setReason(issue.issue());
            patch.setStatus("PENDING");
            patch.setCreatedBy(userId);
            patchMapper.insert(patch);
            testCaseMapper.linkAcceptancePatch(request.generationId(), request.issueIndex(), patch.getId());
            return PatchService.toDetail(patchMapper.selectById(patch.getId()));
        } catch (BizException | StalePatchException e) { throw e; }
        catch (Exception e) { throw new BizException("无法生成验收标准补丁"); }
    }

    private PrdVersion requireVersion(UUID versionId, UUID userId) {
        PrdVersion version = prdVersionMapper.selectById(versionId);
        if (version == null) throw new NotFoundException("PRD 版本不存在");
        permissionService.requireView(version.getPrdId(), userId);
        return version;
    }

    private JsonNode findBlock(JsonNode node, String id) {
        if (id.equals(node.path("attrs").path("blockId").asText())) return node;
        for (JsonNode child : node.path("content")) {
            JsonNode found = findBlock(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BizException("测试生成报告保存失败"); }
    }

    public List<TestCaseDetail> list(UUID versionId, UUID userId) {
        PrdVersion version = prdVersionMapper.selectById(versionId);
        if (version == null) {
            throw new NotFoundException("PRD 版本不存在");
        }
        permissionService.requireView(version.getPrdId(), userId);
        List<TestCaseDetail> details = new ArrayList<>();
        for (TestCase entity : testCaseMapper.selectByVersion(versionId)) {
            details.add(toDetail(entity, parseSteps(entity.getSteps())));
        }
        return details;
    }

    private List<ReviewBlockLinearizer.BlockEntry> linearize(PrdVersion version) {
        JsonNode doc;
        try {
            doc = objectMapper.readTree(version.getEditorJson());
        } catch (Exception e) {
            throw new BizException("版本快照解析失败，无法生成测试用例");
        }
        return ReviewBlockLinearizer.linearize(doc);
    }

    private List<String> cleanSteps(List<String> steps) {
        List<String> cleaned = new ArrayList<>();
        if (steps != null) {
            for (String step : steps) {
                if (StringUtils.hasText(step)) {
                    cleaned.add(step.trim());
                }
            }
        }
        return cleaned;
    }

    private String toStepsJson(List<String> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new BizException("测试步骤序列化失败");
        }
    }

    private List<String> parseSteps(String stepsJson) {
        if (!StringUtils.hasText(stepsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stepsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private TestCaseDetail toDetail(TestCase entity, List<String> steps) {
        return new TestCaseDetail(
                entity.getId(),
                entity.getScenario(),
                entity.getPreconditions(),
                steps,
                entity.getInputData(),
                entity.getExpectedResult(),
                entity.getCaseType(),
                entity.getSourceBlockId(),
                entity.getCreatedAt());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
