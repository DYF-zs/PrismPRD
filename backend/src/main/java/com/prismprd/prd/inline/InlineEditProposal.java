package com.prismprd.prd.inline;

import lombok.Data;

@Data
public class InlineEditProposal {

    /** INLINE / BLOCK */
    private String scope;

    /** REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE */
    private String operation;

    private String targetBlockId;

    private String sourceQuote;

    private String newContentMarkdown;

    private String reason;
}
