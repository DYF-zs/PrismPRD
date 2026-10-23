package com.prismprd.prd.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.common.BizException;
import com.prismprd.common.StalePatchException;
import com.prismprd.prd.document.editor.MarkdownTiptapConverter;
import com.prismprd.prd.document.editor.TiptapBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Patch 应用到 TipTap 草稿 JSON 的纯逻辑组件。
 * 校验失败（目标 Block 缺失、原文片段无法匹配）抛出 {@link StalePatchException}。
 */
public final class PatchApplier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PatchApplier() {
    }

    /** 补丁规格（与持久化字段一一对应的应用视图） */
    public record PatchSpec(PatchScope scope,
                            PatchOperation operation,
                            String targetBlockId,
                            String sourceQuote,
                            String newContentMarkdown) {
    }

    /** 目标 Block 定位：所在 content 数组 + 下标 + 节点 */
    private record BlockRef(ArrayNode parent, int index, ObjectNode node) {
    }

    /**
     * 应用补丁，返回新的 doc（输入不被修改）。
     */
    public static ObjectNode apply(ObjectNode draftDoc, PatchSpec spec) {
        BlockRef target = locateBlock(draftDoc, spec.targetBlockId());
        if (target == null) {
            throw new StalePatchException("STALE_PATCH：目标 Block（" + spec.targetBlockId() + "）在当前草稿中不存在，请重新生成修复建议");
        }
        ObjectNode doc = draftDoc.deepCopy();
        BlockRef copy = locateBlock(doc, spec.targetBlockId());
        return switch (spec.scope()) {
            case BLOCK -> applyBlock(doc, copy, spec);
            case INLINE -> applyInline(doc, copy, spec);
        };
    }

    /**
     * 校验目标 Block 在草稿中是否存在（生成补丁前使用）。
     */
    public static boolean blockExists(JsonNode doc, String blockId) {
        return blockId != null && !blockId.isBlank() && locateBlock(doc, blockId) != null;
    }

    /** 将 Markdown 片段解析为 Block 节点数组（供 Inline Edit 预览转换使用） */
    public static ArrayNode parseBlockNodes(String markdown) {
        return parseBlockContent(markdown);
    }

    /** 将受限内联 Markdown 解析为 Inline 节点列表（供 Inline Edit 预览转换使用） */
    public static List<JsonNode> parseInlineNodes(String markdown) {
        return parseInlineContent(markdown, PatchOperation.REPLACE);
    }

    // ==================== BLOCK 级操作 ====================

    private static ObjectNode applyBlock(ObjectNode doc, BlockRef target, PatchSpec spec) {
        if (spec.sourceQuote() != null && !spec.sourceQuote().isBlank()
                && !plainText(target.node()).contains(spec.sourceQuote())) {
            throw new StalePatchException("STALE_PATCH：目标 Block 无法匹配原文，请重新生成");
        }
        if (spec.operation() == PatchOperation.DELETE) {
            target.parent().remove(target.index());
            return doc;
        }
        ArrayNode newBlocks = parseBlockContent(spec.newContentMarkdown());
        switch (spec.operation()) {
            case REPLACE -> {
                target.parent().remove(target.index());
                for (int i = 0; i < newBlocks.size(); i++) {
                    target.parent().insert(target.index() + i, newBlocks.get(i));
                }
            }
            case INSERT_BEFORE -> {
                for (int i = 0; i < newBlocks.size(); i++) {
                    target.parent().insert(target.index() + i, newBlocks.get(i));
                }
            }
            case INSERT_AFTER -> {
                for (int i = 0; i < newBlocks.size(); i++) {
                    target.parent().insert(target.index() + 1 + i, newBlocks.get(i));
                }
            }
            default -> throw new BizException("BLOCK scope 不支持操作：" + spec.operation());
        }
        return doc;
    }

    private static ArrayNode parseBlockContent(String markdown) {
        String content = markdown == null ? "" : markdown;
        if (content.isBlank()) {
            throw new BizException("AI 生成的修复内容为空，请重试");
        }
        try {
            JsonNode fragment = MAPPER.readTree(MarkdownTiptapConverter.convert(content));
            JsonNode contentArray = fragment.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new BizException("AI 生成的修复内容为空，请重试");
            }
            return (ArrayNode) contentArray;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("修复内容 Markdown 解析失败：" + e.getMessage());
        }
    }

    // ==================== INLINE 级操作 ====================

    private static ObjectNode applyInline(ObjectNode doc, BlockRef target, PatchSpec spec) {
        switch (spec.operation()) {
            case DELETE -> removeQuote(target, spec.sourceQuote());
            case REPLACE -> replaceQuote(target, spec);
            case INSERT_BEFORE, INSERT_AFTER -> replaceQuote(target, spec);
            default -> throw new BizException("INLINE scope 不支持操作：" + spec.operation());
        }
        return doc;
    }

    private static void replaceQuote(BlockRef target, PatchSpec spec) {
        List<JsonNode> inline = parseInlineContent(spec.newContentMarkdown(), spec.operation());
        String quote = requireQuote(spec.sourceQuote());

        // 按 content 数组分组寻找 quote 覆盖的 text 节点区间（quote 必须落在同一数组内）
        List<List<ObjectNode>> groups = new ArrayList<>();
        List<ArrayNode> groupArrays = new ArrayList<>();
        groupTextNodes(target.node().path("content"), groups, groupArrays);

        int matches = 0;
        for (List<ObjectNode> group : groups) {
            String text = group.stream().map(n -> n.path("text").asText("")).collect(java.util.stream.Collectors.joining());
            for (int at = text.indexOf(quote); at >= 0; at = text.indexOf(quote, at + 1)) matches++;
        }
        if (matches > 1) {
            throw new StalePatchException("STALE_PATCH：原文片段不唯一，请选择更完整的文本后重新生成");
        }

        for (int g = 0; g < groups.size(); g++) {
            List<ObjectNode> group = groups.get(g);
            StringBuilder sb = new StringBuilder();
            List<Integer> starts = new ArrayList<>();
            for (ObjectNode textNode : group) {
                starts.add(sb.length());
                sb.append(textNode.path("text").asText(""));
            }
            int start = sb.indexOf(quote);
            if (start < 0) {
                continue;
            }
            int end = start + quote.length();
            ArrayNode host = groupArrays.get(g);
            int idxStart = -1;
            int idxEnd = -1;
            for (int i = 0; i < group.size(); i++) {
                int nodeStart = starts.get(i);
                int nodeEnd = nodeStart + group.get(i).path("text").asText("").length();
                if (nodeEnd > start && nodeStart < end) {
                    if (idxStart < 0) {
                        idxStart = indexOfNode(host, group.get(i));
                    }
                    idxEnd = indexOfNode(host, group.get(i));
                }
            }
            if (idxStart < 0) {
                continue;
            }
            ObjectNode firstNode = group.get(findIndexByHost(host, group, idxStart));
            ObjectNode lastNode = group.get(findIndexByHost(host, group, idxEnd));
            int firstNodeStart = starts.get(findIndexByHost(host, group, idxStart));
            int lastNodeStart = starts.get(findIndexByHost(host, group, idxEnd));
            int lastNodeEnd = lastNodeStart + lastNode.path("text").asText("").length();
            String prefix = sb.substring(firstNodeStart, start);
            String suffix = lastNodeEnd > end ? sb.substring(end, lastNodeEnd) : "";

            List<JsonNode> replacement = new ArrayList<>();
            if (!prefix.isEmpty()) {
                replacement.add(textNode(prefix, firstNode));
            }
            if (spec.operation() == PatchOperation.INSERT_BEFORE || spec.operation() == PatchOperation.INSERT_AFTER) {
                List<JsonNode> original = new ArrayList<>();
                for (int i = 0; i < group.size(); i++) {
                    int nodeStart = starts.get(i);
                    String text = group.get(i).path("text").asText("");
                    int sliceStart = Math.max(start - nodeStart, 0);
                    int sliceEnd = Math.min(end - nodeStart, text.length());
                    if (sliceEnd > sliceStart) original.add(textNode(text.substring(sliceStart, sliceEnd), group.get(i)));
                }
                if (spec.operation() == PatchOperation.INSERT_AFTER) replacement.addAll(original);
                replacement.addAll(inline);
                if (spec.operation() == PatchOperation.INSERT_BEFORE) replacement.addAll(original);
            } else {
                replacement.addAll(inline);
            }
            if (!suffix.isEmpty()) {
                replacement.add(textNode(suffix, lastNode));
            }
            for (int i = 0; i <= idxEnd - idxStart; i++) {
                host.remove(idxStart);
            }
            for (int i = 0; i < replacement.size(); i++) {
                host.insert(idxStart + i, replacement.get(i));
            }
            return;
        }
        throw new StalePatchException("STALE_PATCH：目标 Block 内容已变化，无法匹配原文片段，请重新生成修复建议");
    }

    private static int findIndexByHost(ArrayNode host, List<ObjectNode> group, int hostIndex) {
        for (int i = 0; i < group.size(); i++) {
            if (indexOfNode(host, group.get(i)) == hostIndex) {
                return i;
            }
        }
        return -1;
    }

    private static void removeQuote(BlockRef target, String sourceQuote) {
        String quote = requireQuote(sourceQuote);
        // 用空替换实现删除（DELETE 允许空内联内容）
        PatchSpec delete = new PatchSpec(PatchScope.INLINE, PatchOperation.DELETE,
                target.node().path("attrs").path("blockId").asText(null), quote, null);
        replaceQuote(target, delete);
    }

    /** 将受限 Markdown Fragment 解析为 Inline 节点列表（取首个 Block 的内联内容） */
    private static List<JsonNode> parseInlineContent(String markdown, PatchOperation operation) {
        String content = markdown == null ? "" : markdown;
        if (content.isBlank()) {
            if (operation == PatchOperation.DELETE) {
                return List.of();
            }
            throw new BizException("AI 生成的修复内容为空，请重试");
        }
        try {
            JsonNode fragment = MAPPER.readTree(MarkdownTiptapConverter.convert(content));
            JsonNode blocks = fragment.path("content");
            if (!blocks.isArray() || blocks.isEmpty()) {
                throw new BizException("INLINE 修复内容必须为纯内联文本");
            }
            if (blocks.size() > 1) {
                throw new BizException("INLINE 修复内容只能是一段内联文本（不支持标题/列表/代码块）");
            }
            JsonNode first = blocks.get(0);
            if (!"paragraph".equals(first.path("type").asText())) {
                throw new BizException("INLINE 修复内容不能包含标题、列表或代码块");
            }
            List<JsonNode> inline = new ArrayList<>();
            if (first.path("content").isArray()) {
                first.path("content").forEach(inline::add);
            }
            return inline;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("修复内容解析失败：" + e.getMessage());
        }
    }

    private static String requireQuote(String quote) {
        if (quote == null || quote.isBlank()) {
            throw new StalePatchException("STALE_PATCH：补丁缺少原文定位片段，无法应用，请重新生成");
        }
        return quote;
    }

    // ==================== 定位与工具 ====================

    private static BlockRef locateBlock(JsonNode doc, String blockId) {
        return findInContent(doc == null ? null : doc.path("content"), blockId);
    }

    private static BlockRef findInContent(JsonNode content, String blockId) {
        if (content == null || !content.isArray()) {
            return null;
        }
        for (int i = 0; i < content.size(); i++) {
            JsonNode node = content.get(i);
            if (node instanceof ObjectNode objectNode) {
                String type = objectNode.path("type").asText("");
                if (TiptapBlocks.ANCHORED_TYPES.contains(type)
                        && blockId.equals(objectNode.path("attrs").path("blockId").asText(null))) {
                    return new BlockRef((ArrayNode) content, i, objectNode);
                }
                BlockRef nested = findInContent(objectNode.path("content"), blockId);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /** 按 content 数组分组收集 text 节点（保持文档顺序） */
    private static void groupTextNodes(JsonNode content, List<List<ObjectNode>> groups, List<ArrayNode> groupArrays) {
        if (content == null || !content.isArray()) {
            return;
        }
        List<ObjectNode> current = new ArrayList<>();
        for (JsonNode node : content) {
            if ("text".equals(node.path("type").asText("")) && node instanceof ObjectNode objectNode) {
                current.add(objectNode);
            } else {
                if (!current.isEmpty()) {
                    groups.add(current);
                    groupArrays.add((ArrayNode) content);
                    current = new ArrayList<>();
                }
                groupTextNodes(node.path("content"), groups, groupArrays);
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
            groupArrays.add((ArrayNode) content);
        }
    }

    private static int indexOfNode(ArrayNode array, JsonNode node) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) == node) {
                return i;
            }
        }
        return -1;
    }

    private static JsonNode textNode(String text, ObjectNode template) {
        ObjectNode result = template.deepCopy();
        result.put("text", text);
        return result;
    }

    private static String plainText(JsonNode node) {
        if ("text".equals(node.path("type").asText())) return node.path("text").asText("");
        StringBuilder text = new StringBuilder();
        node.path("content").forEach(child -> text.append(plainText(child)));
        return text.toString();
    }
}
