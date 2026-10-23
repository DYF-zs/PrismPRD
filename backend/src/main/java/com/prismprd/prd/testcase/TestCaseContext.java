package com.prismprd.prd.testcase;

import com.prismprd.review.ReviewBlockLinearizer;

import java.util.List;
import java.util.UUID;

/** 测试用例生成上下文：固定 PRD Version 快照 + 可选聚焦指令 */
public record TestCaseContext(UUID prdId,
                              String prdTitle,
                              Integer versionNo,
                              String instruction,
                              List<ReviewBlockLinearizer.BlockEntry> blocks,
                              String annotatedMarkdown) {
}
