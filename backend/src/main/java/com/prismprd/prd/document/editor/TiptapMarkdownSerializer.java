package com.prismprd.prd.document.editor;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class TiptapMarkdownSerializer {

    private TiptapMarkdownSerializer() {
    }

    public static String serialize(JsonNode doc) {
        List<String> blocks = new ArrayList<>();
        JsonNode content = doc == null ? null : doc.path("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                String rendered = renderBlock(block, 0);
                if (rendered != null && !rendered.isBlank()) {
                    blocks.add(rendered);
                }
            }
        }
        return String.join("\n\n", blocks);
    }

    private static String renderBlock(JsonNode node, int level) {
        switch (node.path("type").asText("")) {
            case "heading" -> {
                int headingLevel = Math.max(1, Math.min(6, node.path("attrs").path("level").asInt(1)));
                return "#".repeat(headingLevel) + " " + renderInline(node.path("content"));
            }
            case "paragraph" -> {
                return renderInline(node.path("content"));
            }
            case "codeBlock" -> {
                String language = node.path("attrs").path("language").asText("");
                String code = plainText(node.path("content"));
                if (!code.endsWith("\n")) {
                    code = code + "\n";
                }
                return "```" + language + "\n" + code + "```";
            }
            case "blockquote" -> {
                String inner = renderChildren(node, level);
                StringBuilder sb = new StringBuilder();
                for (String line : inner.split("\n", -1)) {
                    sb.append(line.isBlank() ? ">" : "> " + line).append("\n");
                }
                return sb.toString().stripTrailing();
            }
            case "bulletList" -> {
                return renderList(node, level, false);
            }
            case "orderedList" -> {
                return renderList(node, level, true);
            }
            case "horizontalRule" -> {
                return "---";
            }
            case "table" -> {
                return renderTable(node);
            }
            default -> {
                return null;
            }
        }
    }

    private static String renderChildren(JsonNode node, int level) {
        List<String> parts = new ArrayList<>();
        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode child : content) {
                String rendered = renderBlock(child, level);
                if (rendered != null && !rendered.isBlank()) {
                    parts.add(rendered);
                }
            }
        }
        return String.join("\n\n", parts);
    }

    private static String renderList(JsonNode listNode, int level, boolean ordered) {
        List<String> itemLines = new ArrayList<>();
        int index = ordered ? Math.max(1, listNode.path("attrs").path("start").asInt(1)) : 1;
        JsonNode content = listNode.path("content");
        if (!content.isArray()) {
            return "";
        }
        for (JsonNode item : content) {
            if (!"listItem".equals(item.path("type").asText())) {
                continue;
            }
            String marker = ordered ? (index + ".") : "-";
            String indent = " ".repeat(level * (ordered ? 3 : 2));
            String pad = indent + " ".repeat(marker.length() + 1);
            List<String> parts = new ArrayList<>();
            JsonNode itemContent = item.path("content");
            if (itemContent.isArray()) {
                for (JsonNode child : itemContent) {
                    String rendered = renderBlock(child, level + 1);
                    if (rendered != null && !rendered.isBlank()) {
                        parts.add(rendered);
                    }
                }
            }
            StringBuilder itemSb = new StringBuilder();
            for (int p = 0; p < parts.size(); p++) {
                String[] lines = parts.get(p).split("\n", -1);
                for (int l = 0; l < lines.length; l++) {
                    if (p == 0 && l == 0) {
                        itemSb.append(indent).append(marker).append(" ").append(lines[l]);
                    } else {
                        itemSb.append("\n").append(lines[l].isBlank() ? pad.stripTrailing() : pad + lines[l]);
                    }
                }
            }
            if (itemSb.length() == 0) {
                itemSb.append(indent).append(marker);
            }
            itemLines.add(itemSb.toString());
            index++;
        }
        return String.join("\n", itemLines);
    }

    private static String renderTable(JsonNode tableNode) {
        List<List<String>> rows = new ArrayList<>();
        JsonNode content = tableNode.path("content");
        if (content.isArray()) {
            for (JsonNode row : content) {
                if (!"tableRow".equals(row.path("type").asText())) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                JsonNode cellsNode = row.path("content");
                if (cellsNode.isArray()) {
                    for (JsonNode cell : cellsNode) {
                        String text = renderInline(cell.path("content")).replace("|", "\\|").replace("\n", " ");
                        cells.add(text);
                    }
                }
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        StringBuilder sb = new StringBuilder();
        sb.append(toRow(rows.get(0), columns));
        sb.append("\n|");
        sb.append(" --- |".repeat(columns));
        for (int r = 1; r < rows.size(); r++) {
            sb.append("\n").append(toRow(rows.get(r), columns));
        }
        return sb.toString();
    }

    private static String toRow(List<String> cells, int columns) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < columns; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            sb.append(" ").append(cell).append(" |");
        }
        return sb.toString();
    }

    private static String renderInline(JsonNode content) {
        if (content == null || !content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : content) {
            switch (node.path("type").asText("")) {
                case "text" -> sb.append(renderText(node.path("text").asText(""), node.path("marks")));
                case "hardBreak" -> sb.append("  \n");
                case "image" -> {
                    JsonNode attrs = node.path("attrs");
                    sb.append("![").append(attrs.path("alt").asText("")).append("](")
                            .append(attrs.path("src").asText("")).append(")");
                }
                default -> {
                }
            }
        }
        return sb.toString();
    }

    private static String renderText(String raw, JsonNode marks) {
        if (raw.isEmpty()) {
            return "";
        }
        boolean code = false;
        if (marks != null && marks.isArray()) {
            for (JsonNode mark : marks) {
                if ("code".equals(mark.path("type").asText())) {
                    code = true;
                }
            }
        }
        if (code) {
            return "`" + raw.replace("`", "'") + "`";
        }
        String text = escapeText(raw);
        if (marks != null && marks.isArray()) {
            for (int i = marks.size() - 1; i >= 0; i--) {
                JsonNode mark = marks.get(i);
                switch (mark.path("type").asText("")) {
                    case "bold" -> text = "**" + text + "**";
                    case "italic" -> text = "*" + text + "*";
                    case "strike" -> text = "~~" + text + "~~";
                    case "link" -> text = "[" + text + "](" + mark.path("attrs").path("href").asText("") + ")";
                    default -> {
                    }
                }
            }
        }
        return text;
    }

    private static String escapeText(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '\\', '`', '*', '_', '[', ']', '<', '|' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String plainText(JsonNode content) {
        if (content == null || !content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : content) {
            if ("text".equals(node.path("type").asText())) {
                sb.append(node.path("text").asText(""));
            } else {
                sb.append(plainText(node.path("content")));
            }
        }
        return sb.toString();
    }
}
