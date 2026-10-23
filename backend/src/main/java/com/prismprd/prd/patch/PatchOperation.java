package com.prismprd.prd.patch;

import com.prismprd.common.BizException;

public enum PatchOperation {
    REPLACE,
    INSERT_BEFORE,
    INSERT_AFTER,
    DELETE;

    public static PatchOperation parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException("非法 Patch operation：" + value);
        }
    }

    public boolean requiresContent() {
        return this != DELETE;
    }
}
