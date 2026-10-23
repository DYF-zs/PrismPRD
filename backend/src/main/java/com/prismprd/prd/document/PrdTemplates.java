package com.prismprd.prd.document;

import com.prismprd.common.BizException;

import java.util.List;

public final class PrdTemplates {

    public record Template(String key, String name, String description, String markdown) {
    }

    private static final String STANDARD_FEATURE = """
            ## 背景与问题

            描述需求的业务背景、现状与要解决的问题。

            ## 目标与成功指标

            - 目标：
            - 成功指标：

            ## 用户与场景

            | 角色 | 场景 | 说明 |
            | --- | --- | --- |
            |  |  |  |

            ## 功能需求

            ### 功能 1：

            **需求描述**：

            **规则与约束**：

            ## 流程说明

            1. 主流程步骤

            ## 异常与边界

            | 场景 | 处理方式 |
            | --- | --- |
            |  |  |

            ## 非功能要求

            - 性能：
            - 安全：
            - 合规：

            ## 验收标准

            - 验收项 1

            ## 范围与里程碑

            - In Scope：
            - Out of Scope：
            """;

    private static final String MVP_LITE = """
            ## 背景与目标

            ## 核心场景

            ## 功能范围

            - In Scope：
            - Out of Scope：

            ## 验收标准

            -
            """;

    public static final List<Template> TEMPLATES = List.of(
            new Template("standard-feature", "标准功能 PRD",
                    "覆盖背景、目标、场景、功能、流程、异常、验收与范围的完整结构。", STANDARD_FEATURE),
            new Template("mvp-lite", "MVP 需求简版",
                    "面向 MVP 的轻量结构，聚焦背景目标、核心场景、范围与验收。", MVP_LITE));

    private PrdTemplates() {
    }

    public static List<Template> list() {
        return TEMPLATES;
    }

    public static String render(String templateKey, String title) {
        Template template = TEMPLATES.stream()
                .filter(t -> t.key().equals(templateKey))
                .findFirst()
                .orElseThrow(() -> new BizException("模板不存在：" + templateKey));
        return "# " + title + "\n\n" + template.markdown();
    }
}
