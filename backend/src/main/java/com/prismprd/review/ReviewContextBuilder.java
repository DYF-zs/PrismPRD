package com.prismprd.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.common.BizException;
import com.prismprd.common.NotFoundException;
import com.prismprd.prd.document.PrdDocument;
import com.prismprd.prd.document.PrdDocumentMapper;
import com.prismprd.prd.version.PrdVersion;
import com.prismprd.prd.version.PrdVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReviewContextBuilder {

    private final PrdVersionMapper prdVersionMapper;
    private final PrdDocumentMapper prdDocumentMapper;
    private final ReviewRubricMapper reviewRubricMapper;
    private final ReviewDimensionMapper reviewDimensionMapper;
    private final ObjectMapper objectMapper;
    private final com.prismprd.knowledge.KnowledgeService knowledgeService;

    public void enrichKnowledge(ReviewContext context, UUID userId) {
        String rubric = context.getDimensions().stream().map(d -> d.getName()+" "+d.getChecklist())
                .collect(java.util.stream.Collectors.joining("\n"));
        String query = context.getPrdTitle() + "\n评审检查项：\n" + rubric.substring(0, Math.min(rubric.length(),800))
                + "\n" + context.getAnnotatedContent();
        context.setKnowledgeEvidence(knowledgeService.search(context.getPrdId(), userId,
                query.substring(0, Math.min(query.length(), 4000)), 8));
    }

    public ReviewContext build(UUID prdVersionId, UUID rubricId) {
        PrdVersion version = prdVersionMapper.selectById(prdVersionId);
        if (version == null) {
            throw new NotFoundException("PRD 版本不存在");
        }
        PrdDocument prd = prdDocumentMapper.selectById(version.getPrdId());
        if (prd == null) {
            throw new NotFoundException("PRD 不存在");
        }
        ReviewRubric rubric = reviewRubricMapper.selectById(rubricId);
        if (rubric == null || !Boolean.TRUE.equals(rubric.getEnabled())) {
            throw new NotFoundException("评审 Rubric 不存在或已停用");
        }
        List<ReviewDimension> dimensions = reviewDimensionMapper.selectByRubric(rubric.getId());
        if (dimensions.isEmpty()) {
            throw new BizException("评审 Rubric 没有配置维度");
        }

        JsonNode doc;
        try {
            doc = objectMapper.readTree(version.getEditorJson());
        } catch (Exception e) {
            throw new BizException("版本快照解析失败，无法评审");
        }
        List<ReviewBlockLinearizer.BlockEntry> blocks = ReviewBlockLinearizer.linearize(doc);
        return new ReviewContext(prd.getId(), version.getTitleSnapshot(), version.getId(), version.getVersionNo(),
                rubric, dimensions, blocks, ReviewBlockLinearizer.renderAnnotated(blocks));
    }
}
