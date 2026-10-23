package com.prismprd.prd.inline;

import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;

@Data
public class InlineEditResponse {
    private java.util.UUID patchId;
    private Long baseDraftRevision;

    /** INLINE / BLOCK */
    private String scope;

    /** REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE */
    private String operation;

    private String targetBlockId;

    private String sourceQuote;

    private String newContentMarkdown;

    /** INLINE：内联节点数组；BLOCK：Block 节点数组；DELETE：空数组 */
    private ArrayNode newContentJson;

    private String reason;

    private String profile;
}
