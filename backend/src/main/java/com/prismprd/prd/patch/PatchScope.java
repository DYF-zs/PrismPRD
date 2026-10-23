package com.prismprd.prd.patch;

import com.prismprd.common.BizException;

public enum PatchScope {
    /** 选区改写与 Block 内插入，只接受内联内容 */
    INLINE,
    /** 段落、标题、列表等 Block 级替换或插入 */
    BLOCK;

    public static PatchScope parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException("非法 Patch scope：" + value);
        }
    }
}
