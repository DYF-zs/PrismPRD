package com.prismprd.prd.patch;

import com.prismprd.common.BizException;

public enum PatchStatus {
    PENDING,
    APPLIED,
    REJECTED,
    STALE;

    public static PatchStatus parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException("非法 Patch status：" + value);
        }
    }
}
