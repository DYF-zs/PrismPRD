package com.prismprd.common;

/**
 * Patch 应用校验失败（草稿 revision 变化、目标 Block 缺失、原文片段无法匹配）。
 * 按 409 返回，前端提示用户基于当前草稿重新生成建议。
 */
public class StalePatchException extends ConflictException {

    public StalePatchException(String message) {
        super(message);
    }
}
