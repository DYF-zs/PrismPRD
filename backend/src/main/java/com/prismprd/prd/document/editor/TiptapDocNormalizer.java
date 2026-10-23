package com.prismprd.prd.document.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.common.BizException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;

public final class TiptapDocNormalizer {

    private static final Set<String> BLOCKS = Set.of("paragraph", "heading", "blockquote", "codeBlock",
            "bulletList", "orderedList", "horizontalRule", "table");
    private static final Set<String> INLINE = Set.of("text", "hardBreak", "image");
    private static final Set<String> MARKS = Set.of("bold", "italic", "strike", "code", "link");

    private TiptapDocNormalizer() {
    }

    public static ObjectNode normalize(JsonNode input) {
        if (input == null || !input.isObject() || !"doc".equals(input.path("type").asText())) {
            throw new BizException("editorJson 必须是以 doc 为根的 TipTap 文档");
        }
        ObjectNode doc = (ObjectNode) input;
        rejectPrototypeKeys(doc);
        validateNode(doc, "doc", 0, new int[]{0});
        if (!doc.has("content") || doc.path("content").isEmpty()) {
            doc.putArray("content").addObject().put("type", "paragraph");
        }
        normalizeChildren(doc, new HashSet<>());
        return doc;
    }

    private static void rejectPrototypeKeys(JsonNode input) {
        var pending = new ArrayDeque<JsonNode>();
        pending.add(input);
        int count = 0;
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeLast();
            if (++count > 100000) throw new BizException("文档字段数超出限制");
            if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (Set.of("__proto__", "prototype", "constructor").contains(field.getKey()))
                        throw new BizException("文档包含不安全的属性键");
                    pending.add(field.getValue());
                }
            } else if (node.isArray()) node.forEach(pending::add);
        }
    }

    private static void validateNode(JsonNode node, String type, int depth, int[] count) {
        if (depth > 64 || ++count[0] > 20000) throw new BizException("文档层级或节点数超出限制");
        if (!node.isObject() || !type.equals(node.path("type").asText())) throw new BizException("文档节点格式错误");
        JsonNode attrs = node.path("attrs");
        if (!attrs.isMissingNode() && !attrs.isNull() && !attrs.isObject()) throw new BizException("节点 attrs 必须是对象");
        JsonNode id = attrs.path("blockId");
        if (!id.isMissingNode() && !id.isNull() && (!id.isTextual() || id.asText().length() > 128))
            throw new BizException("blockId 必须是最长 128 字符的字符串");
        JsonNode content = node.path("content");
        if (!content.isMissingNode() && !content.isArray()) throw new BizException("节点 content 必须是数组");
        if ("text".equals(type) && (!node.path("text").isTextual() || node.path("text").asText().isEmpty()))
            throw new BizException("text 节点必须包含非空文本");
        if ("heading".equals(type) && attrs.has("level") && (!attrs.path("level").isIntegralNumber()
                || attrs.path("level").asInt() < 1 || attrs.path("level").asInt() > 6))
            throw new BizException("标题级别必须为 1 至 6");
        if ("image".equals(type)) validateUrl(attrs.path("src").asText(""));
        if ("tableCell".equals(type) || "tableHeader".equals(type)) {
            for (String span : Set.of("colspan", "rowspan")) {
                if (attrs.has(span) && (!attrs.path(span).isIntegralNumber() || attrs.path(span).asInt() < 1
                        || attrs.path(span).asInt() > 100)) throw new BizException("表格跨度必须在 1 至 100 之间");
            }
        }
        JsonNode marks = node.path("marks");
        if (!marks.isMissingNode() && !marks.isArray()) throw new BizException("marks 必须是数组");
        if (marks.isArray()) for (JsonNode mark : marks) {
            if (!mark.isObject() || !MARKS.contains(mark.path("type").asText())) throw new BizException("不支持的文本标记");
            if ("link".equals(mark.path("type").asText())) validateUrl(mark.path("attrs").path("href").asText(""));
        }
        Set<String> allowed = switch (type) {
            case "doc", "blockquote", "listItem" -> BLOCKS;
            case "paragraph", "heading", "tableCell", "tableHeader" -> INLINE;
            case "codeBlock" -> Set.of("text");
            case "bulletList", "orderedList" -> Set.of("listItem");
            case "table" -> Set.of("tableRow");
            case "tableRow" -> Set.of("tableCell", "tableHeader");
            case "text", "hardBreak", "image", "horizontalRule" -> Set.of();
            default -> throw new BizException("不支持的文档节点：" + type);
        };
        if (Set.of("blockquote", "bulletList", "orderedList", "listItem", "table", "tableRow").contains(type)
                && content.isEmpty()) throw new BizException("容器节点不能为空：" + type);
        if ("listItem".equals(type) && !"paragraph".equals(content.path(0).path("type").asText()))
            throw new BizException("列表项必须以段落开始");
        if (content.isArray()) for (JsonNode child : content) {
            String childType = child.path("type").asText("");
            if (!allowed.contains(childType)) throw new BizException("节点 " + type + " 不允许包含 " + childType);
            validateNode(child, childType, depth + 1, count);
        }
    }

    private static void validateUrl(String url) {
        String value = url.trim();
        if (value.chars().anyMatch(c -> c < 32 || c == 127) || value.contains("\\"))
            throw new BizException("链接地址包含非法字符");
        if (value.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !value.matches("(?i)^(https?://|mailto:).*"))
            throw new BizException("链接仅支持 HTTP、HTTPS、邮件或相对地址");
    }

    private static void normalizeChildren(JsonNode parent, Set<String> seenBlockIds) {
        JsonNode content = parent.path("content");
        if (!content.isArray()) {
            return;
        }
        for (JsonNode child : content) {
            if (!(child instanceof ObjectNode node)) {
                continue;
            }
            String type = node.path("type").asText("");
            if (TiptapBlocks.ANCHORED_TYPES.contains(type)) {
                ObjectNode attrs = node.path("attrs").isObject()
                        ? (ObjectNode) node.path("attrs")
                        : node.putObject("attrs");
                String blockId = attrs.path("blockId").asText(null);
                if (blockId == null || blockId.isBlank() || !seenBlockIds.add(blockId)) {
                    String generated = UUID.randomUUID().toString();
                    attrs.put("blockId", generated);
                    seenBlockIds.add(generated);
                }
            }
            normalizeChildren(node, seenBlockIds);
        }
    }
}
