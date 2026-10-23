package com.prismprd.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prismprd.common.BizException;
import com.prismprd.common.ConflictException;
import com.prismprd.common.NotFoundException;
import com.prismprd.common.PermissionDeniedException;
import com.prismprd.security.CurrentUserService;
import com.prismprd.web.dto.DimensionDetail;
import com.prismprd.web.dto.PublishRubricRequest;
import com.prismprd.web.dto.RubricDetail;
import com.prismprd.web.dto.RubricSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RubricService {

    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ReviewRubricMapper rubricMapper;
    private final ReviewDimensionMapper dimensionMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final RubricRulesValidator rulesValidator;

    @Transactional
    public RubricDetail publishNew(PublishRubricRequest request) {
        if (request.baseRubricId() != null) {
            throw new BizException("新建标准不应提供 baseRubricId；发布新版本请使用版本接口");
        }
        rulesValidator.validate(request);
        return publish(request, "custom-" + UUID.randomUUID(), 1, currentUserService.currentUserId());
    }

    @Transactional
    public RubricDetail publishVersion(String key, PublishRubricRequest request) {
        if (key == null || !key.matches("[a-zA-Z0-9-]{1,64}")) throw new BizException("标准标识不合法");
        if (request.baseRubricId() == null) throw new BizException("发布新版本需提供 baseRubricId");
        UUID user = currentUserService.currentUserId();
        ReviewRubric base = rubricMapper.selectById(request.baseRubricId());
        if (base == null || !key.equals(base.getRubricKey())) throw new NotFoundException("基础标准版本不存在");
        if (isBuiltin(base) || !user.equals(base.getCreatedBy())) {
            throw new PermissionDeniedException("只能为自己创建的标准发布新版本；内置标准请复制后使用");
        }
        // Serialize publishers of the same base, then re-read latest after any lock wait.
        rubricMapper.lockById(base.getId());
        ReviewRubric latest = rubricMapper.selectLatestByKey(key);
        if (latest == null || !latest.getId().equals(base.getId())) {
            throw new ConflictException("标准已有新版本，请重新加载最新版本后编辑；当前输入已保留");
        }
        if (latest.getVersion() >= 10000) throw new BizException("该标准已达到版本数量上限");
        rulesValidator.validate(request);
        return publish(request, key, latest.getVersion() + 1, user);
    }

    public List<RubricSummary> versions(UUID id) {
        ReviewRubric rubric = readRubric(id);
        return rubricMapper.selectVersions(rubric.getRubricKey()).stream().map(this::summary).toList();
    }

    private RubricDetail publish(PublishRubricRequest request, String key, int version, UUID user) {
        var rubric = new ReviewRubric();
        rubric.setId(UUID.randomUUID());
        rubric.setRubricKey(key);
        rubric.setName(request.name().strip());
        rubric.setDescription(cleanText(request.description()));
        rubric.setVersion(version);
        rubric.setEnabled(true);
        rubric.setCreatedBy(user);
        rubricMapper.insert(rubric);
        int order = 0;
        for (var input : request.dimensions()) {
            var dimension = new ReviewDimension();
            dimension.setId(UUID.randomUUID());
            dimension.setRubricId(rubric.getId());
            dimension.setDimensionKey(input.dimensionKey());
            dimension.setName(input.name().strip());
            dimension.setDescription(cleanText(input.description()));
            dimension.setChecklist(json(input.checklist()));
            dimension.setScoreAnchor(json(input.scoreAnchor()));
            dimension.setSeverityAnchor(json(input.severityAnchor()));
            dimension.setApplicabilityRule(input.applicabilityRule());
            dimension.setDisplayOrder(++order);
            dimensionMapper.insert(dimension);
        }
        return getRubric(rubric.getId());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BizException("评审规则无法序列化"); }
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.strip();
    }

    private boolean isBuiltin(ReviewRubric rubric) { return SYSTEM_USER.equals(rubric.getCreatedBy()); }

    private boolean canEdit(ReviewRubric rubric) {
        return !isBuiltin(rubric) && currentUserService.currentUserId().equals(rubric.getCreatedBy());
    }

    private RubricSummary summary(ReviewRubric rubric) {
        var result = new RubricSummary();
        result.setId(rubric.getId());
        result.setRubricKey(rubric.getRubricKey());
        result.setName(rubric.getName());
        result.setVersion(rubric.getVersion());
        result.setDescription(rubric.getDescription());
        result.setCreatedAt(rubric.getCreatedAt());
        result.setDimensionCount(dimensionMapper.selectByRubric(rubric.getId()).size());
        result.setBuiltin(isBuiltin(rubric));
        result.setEditable(canEdit(rubric));
        return result;
    }

    public List<RubricSummary> listRubrics() {
        List<RubricSummary> result = new ArrayList<>();
        for (ReviewRubric rubric : rubricMapper.selectEnabledLatest()) {
            result.add(summary(rubric));
        }
        return result;
    }

    public RubricDetail getRubric(UUID id) {
        return toDetail(readRubric(id));
    }

    private RubricDetail toDetail(ReviewRubric rubric) {
        RubricDetail detail = new RubricDetail();
        detail.setId(rubric.getId());
        detail.setRubricKey(rubric.getRubricKey());
        detail.setName(rubric.getName());
        detail.setVersion(rubric.getVersion());
        detail.setDescription(rubric.getDescription());
        detail.setCreatedAt(rubric.getCreatedAt());
        detail.setBuiltin(isBuiltin(rubric));
        detail.setEditable(canEdit(rubric));
        List<DimensionDetail> dimensions = new ArrayList<>();
        for (ReviewDimension dimension : dimensionMapper.selectByRubric(rubric.getId())) {
            DimensionDetail dto = new DimensionDetail();
            dto.setDimensionKey(dimension.getDimensionKey());
            dto.setName(dimension.getName());
            dto.setDescription(dimension.getDescription());
            dto.setChecklist(parseStrings(dimension.getChecklist()));
            dto.setScoreAnchor(parseTree(dimension.getScoreAnchor()));
            dto.setSeverityAnchor(parseTree(dimension.getSeverityAnchor()));
            dto.setApplicabilityRule(dimension.getApplicabilityRule());
            dto.setDisplayOrder(dimension.getDisplayOrder());
            dimensions.add(dto);
        }
        detail.setDimensions(dimensions);
        return detail;
    }

    private ReviewRubric readRubric(UUID id) {
        ReviewRubric rubric = rubricMapper.selectById(id);
        if (rubric == null) throw new NotFoundException("评审标准版本不存在");
        return rubric;
    }

    private List<String> parseStrings(String json) {
        try {
            List<String> values = objectMapper.readValue(json == null ? "[]" : json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values;
        } catch (Exception e) {
            throw new BizException("Rubric Checklist 解析失败");
        }
    }

    private JsonNode parseTree(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            throw new BizException("Rubric Anchor 解析失败");
        }
    }
}
