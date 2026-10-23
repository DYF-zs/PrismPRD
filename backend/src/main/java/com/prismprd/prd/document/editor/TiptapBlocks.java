package com.prismprd.prd.document.editor;

import java.util.Set;

public final class TiptapBlocks {

    public static final Set<String> ANCHORED_TYPES = Set.of(
            "heading", "paragraph", "blockquote", "codeBlock", "listItem", "tableRow", "horizontalRule");

    private TiptapBlocks() {
    }
}
