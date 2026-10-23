package com.prismprd.review;

import com.prismprd.common.BizException;
import com.prismprd.web.dto.PublishRubricRequest;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class RubricRulesValidator {
    public void validate(PublishRubricRequest request) {
        if (request.dimensions()==null || request.dimensions().isEmpty() || request.dimensions().size()>24)
            throw new BizException("评审标准需包含 1–24 个维度");
        Set<String> keys=new HashSet<>();
        for (var dimension : request.dimensions()) {
            if (dimension==null || dimension.dimensionKey()==null || !keys.add(dimension.dimensionKey()))
                throw new BizException("维度标识不能为空或重复");
            if (dimension.checklist()==null || dimension.checklist().isEmpty()) throw new BizException("每个维度至少需要一个检查项");
            if (dimension.severityAnchor()==null || !dimension.severityAnchor().keySet().equals(Set.of("CRITICAL","MAJOR","MINOR","SUGGESTION")))
                throw new BizException("每个维度需配置四种严重程度规则");
            if (dimension.scoreAnchor()==null || dimension.scoreAnchor().isEmpty()) throw new BizException("缺少评分区间");
            boolean[] covered=new boolean[101];
            for (var band : dimension.scoreAnchor()) {
                int min, max;
                try {
                    min=band.min().movePointRight(1).intValueExact();
                    max=band.max().movePointRight(1).intValueExact();
                } catch (Exception e) { throw new BizException("评分区间必须是 0–10 之间至多一位小数的数字"); }
                if (min<0 || max>100 || min>max) throw new BizException("评分区间的起止值不合法");
                for (int i=min;i<=max;i++) {
                    if (covered[i]) throw new BizException("维度「"+dimension.name()+"」的评分区间重叠");
                    covered[i]=true;
                }
            }
            for (boolean value : covered) if (!value) throw new BizException("维度「"+dimension.name()+"」的评分区间需完整覆盖 0–10 分，精确到 0.1 分");
        }
    }
}
