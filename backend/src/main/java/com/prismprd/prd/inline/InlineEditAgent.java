package com.prismprd.prd.inline;

public interface InlineEditAgent {

    InlineEditProposal edit(InlineEditContext context);

    String profile();
}
