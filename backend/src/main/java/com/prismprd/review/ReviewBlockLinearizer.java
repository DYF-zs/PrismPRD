package com.prismprd.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.prismprd.prd.document.editor.TiptapBlocks;

import java.util.ArrayList;
import java.util.List;

public final class ReviewBlockLinearizer {

    public record BlockEntry(String blockId, String type, String markdown) {
    }

    private ReviewBlockLinearizer() {
    }

    public static List<BlockEntry> linearize(JsonNode doc) {
        List<BlockEntry> entries = new ArrayList<>();
        walk(doc == null ? null : doc.path("content"), 0, entries);
        return entries;
    }

    public static String renderAnnotated(List<BlockEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (BlockEntry entry : entries) {
            sb.append('(').append(entry.blockId()).append(") ").append(entry.markdown()).append('\n');
        }
        return sb.toString();
    }

    private static void walk(JsonNode content, int depth, List<BlockEntry> entries) {
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode node : content) {
            String type = node.path("type").asText("");
            if (TiptapBlocks.ANCHORED_TYPES.contains(type)) {
                String blockId = node.path("attrs").path("blockId").asText(null);
                if (blockId != null && !blockId.isBlank()) {
                    String fragment = fragment(node, depth);
                    if (fragment != null && !fragment.isBlank()) {
                        entries.add(new BlockEntry(blockId, type, fragment));
                    }
                }
            }
            walk(node.path("content"), "listItem".equals(type) ? depth + 1 : depth, entries);
        }
    }

    private static String fragment(JsonNode node, int depth) {
        switch (node.path("type").asText("")) {
            case "heading" -> {
                int level = clamp(node.path("attrs").path("level").asInt(1), 1, 6);
                return "#".repeat(level) + " " + inlineText(node);
            }
            case "paragraph" -> {
                return inlineText(node);
            }
            case "codeBlock" -> {
                String language = node.path("attrs").path("language").asText("");
                String code = inlineText(node);
                return "```" + language + "\n" + code + "\n```";
            }
            case "blockquote" -> {
                return "> " + childrenText(node);
            }
            case "listItem" -> {
                return "  ".repeat(depth) + "- " + childrenText(node);
            }
            case "tableRow" -> {
                List<String> cells = new ArrayList<>();
                JsonNode content = node.path("content");
                if (content.isArray()) {
                    for (JsonNode cell : content) {
                        cells.add(inlineText(cell).replace("|", "\\|"));
                    }
                }
                return "| " + String.join(" | ", cells) + " |";
            }
            case "horizontalRule" -> {
                return "---";
            }
            default -> {
                return null;
            }
        }
    }

    private static String childrenText(JsonNode node) {
        List<String> parts = new ArrayList<>();
        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode child : content) {
                String type = child.path("type").asText("");
                if ("bulletList".equals(type) || "orderedList".equals(type) || "table".equals(type)) {
                    continue;
                }
                String text = inlineText(child);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
        }
        return String.join(" ", parts);
    }

    private static String inlineText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        appendInline(node.path("content"), sb);
        return sb.toString();
    }

    private static void appendInline(JsonNode content, StringBuilder sb) {
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode node : content) {
            switch (node.path("type").asText("")) {
                case "text" -> sb.append(node.path("text").asText(""));
                case "hardBreak" -> sb.append('\n');
                default -> appendInline(node.path("content"), sb);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
