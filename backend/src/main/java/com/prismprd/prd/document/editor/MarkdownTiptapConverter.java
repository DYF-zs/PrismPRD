package com.prismprd.prd.document.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prismprd.common.BizException;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class MarkdownTiptapConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Parser PARSER = Parser.builder()
            .extensions(Arrays.asList(TablesExtension.create()))
            .build();

    private MarkdownTiptapConverter() {
    }

    public static String convert(String markdown) {
        if (markdown != null && markdown.length() > 500000) throw new BizException("Markdown 最长 500000 字符");
        try {
            Node root = PARSER.parse(markdown == null ? "" : markdown);
            ObjectNode doc = MAPPER.createObjectNode();
            doc.put("type", "doc");
            ArrayNode content = doc.putArray("content");
            for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
                JsonNode block = convertBlock(child);
                if (block != null) {
                    content.add(block);
                }
            }
            return MAPPER.writeValueAsString(doc);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Markdown 解析失败：" + e.getMessage());
        }
    }

    public static String emptyDoc() {
        ObjectNode doc = MAPPER.createObjectNode();
        doc.put("type", "doc");
        ObjectNode paragraph = doc.putArray("content").addObject();
        paragraph.put("type", "paragraph");
        paragraph.putObject("attrs").put("blockId", newBlockId());
        try {
            return MAPPER.writeValueAsString(doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode convertBlock(Node node) {
        ensureDepth(node);
        if (node instanceof Heading heading) {
            ObjectNode result = object("heading");
            result.putObject("attrs").put("level", heading.getLevel()).put("blockId", newBlockId());
            appendInline(result, node);
            return result;
        }
        if (node instanceof Paragraph) {
            ObjectNode result = object("paragraph");
            result.putObject("attrs").put("blockId", newBlockId());
            appendInline(result, node);
            return result;
        }
        if (node instanceof BulletList list) {
            ObjectNode result = object("bulletList");
            ArrayNode items = result.putArray("content");
            for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    items.add(convertListItem(child));
                }
            }
            return result;
        }
        if (node instanceof OrderedList list) {
            ObjectNode result = object("orderedList");
            result.putObject("attrs").put("start", list.getStartNumber());
            ArrayNode items = result.putArray("content");
            for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    items.add(convertListItem(child));
                }
            }
            return result;
        }
        if (node instanceof BlockQuote quote) {
            ObjectNode result = object("blockquote");
            result.putObject("attrs").put("blockId", newBlockId());
            ArrayNode content = result.putArray("content");
            for (Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
                JsonNode block = convertBlock(child);
                if (block != null) {
                    content.add(block);
                }
            }
            return result;
        }
        if (node instanceof FencedCodeBlock code) {
            ObjectNode result = object("codeBlock");
            ObjectNode attrs = result.putObject("attrs");
            attrs.put("blockId", newBlockId());
            String language = code.getInfo();
            if (language != null && !language.isBlank()) {
                attrs.put("language", language.trim());
            }
            if (!code.getLiteral().isEmpty()) result.putArray("content").add(textNode(code.getLiteral(), List.of()));
            return result;
        }
        if (node instanceof IndentedCodeBlock code) {
            ObjectNode result = object("codeBlock");
            result.putObject("attrs").put("blockId", newBlockId());
            if (!code.getLiteral().isEmpty()) result.putArray("content").add(textNode(code.getLiteral(), List.of()));
            return result;
        }
        if (node instanceof ThematicBreak) {
            ObjectNode result = object("horizontalRule");
            result.putObject("attrs").put("blockId", newBlockId());
            return result;
        }
        if (node instanceof HtmlBlock html) {
            ObjectNode result = object("paragraph");
            result.putObject("attrs").put("blockId", newBlockId());
            result.putArray("content").add(textNode(html.getLiteral(), List.of()));
            return result;
        }
        if (node instanceof TableBlock) {
            ObjectNode result = object("table");
            ArrayNode rows = result.putArray("content");
            collectTableRows(node, rows);
            return result;
        }
        return null;
    }

    private static JsonNode convertListItem(Node listItem) {
        ObjectNode result = object("listItem");
        result.putObject("attrs").put("blockId", newBlockId());
        ArrayNode content = result.putArray("content");
        for (Node child = listItem.getFirstChild(); child != null; child = child.getNext()) {
            JsonNode block = convertBlock(child);
            if (block != null) {
                content.add(block);
            }
        }
        return result;
    }

    private static void collectTableRows(Node container, ArrayNode rows) {
        boolean headerRow = container instanceof TableHead;
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableRow) {
                ObjectNode row = object("tableRow");
                row.putObject("attrs").put("blockId", newBlockId());
                ArrayNode cells = row.putArray("content");
                for (Node cell = child.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (cell instanceof TableCell) {
                        cells.add(convertTableCell(cell, headerRow));
                    }
                }
                rows.add(row);
            } else {
                collectTableRows(child, rows);
            }
        }
    }

    private static JsonNode convertTableCell(Node cell, boolean header) {
        ObjectNode result = object(header ? "tableHeader" : "tableCell");
        result.putObject("attrs").put("colspan", 1).put("rowspan", 1);
        appendInline(result, cell);
        return result;
    }

    private static void appendInline(ObjectNode parent, Node container) {
        ArrayNode content = null;
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            List<JsonNode> nodes = convertInline(child, List.of());
            if (!nodes.isEmpty()) {
                if (content == null) {
                    content = parent.putArray("content");
                }
                nodes.forEach(content::add);
            }
        }
    }

    private static List<JsonNode> convertInline(Node node, List<ObjectNode> marks) {
        ensureDepth(node);
        if (node instanceof Text text) {
            return List.of(textNode(text.getLiteral(), marks));
        }
        if (node instanceof Code code) {
            return List.of(textNode(code.getLiteral(), withMark(marks, object("code"))));
        }
        if (node instanceof Emphasis) {
            return collectInlineChildren(node, withMark(marks, object("italic")));
        }
        if (node instanceof StrongEmphasis) {
            return collectInlineChildren(node, withMark(marks, object("bold")));
        }
        if (node instanceof Link link) {
            ObjectNode mark = object("link");
            ObjectNode attrs = mark.putObject("attrs");
            if (link.getDestination() != null) {
                attrs.put("href", link.getDestination());
            }
            if (link.getTitle() != null) {
                attrs.put("title", link.getTitle());
            }
            return collectInlineChildren(node, withMark(marks, mark));
        }
        if (node instanceof Image image) {
            ObjectNode result = object("image");
            ObjectNode attrs = result.putObject("attrs");
            attrs.put("src", image.getDestination() == null ? "" : image.getDestination());
            attrs.put("alt", plainText(image));
            if (image.getTitle() != null) {
                attrs.put("title", image.getTitle());
            }
            return List.of(result);
        }
        if (node instanceof SoftLineBreak) {
            return List.of(textNode(" ", marks));
        }
        if (node instanceof HardLineBreak) {
            return List.of(object("hardBreak"));
        }
        if (node instanceof HtmlInline) {
            return List.of();
        }
        return collectInlineChildren(node, marks);
    }

    private static List<JsonNode> collectInlineChildren(Node container, List<ObjectNode> marks) {
        List<JsonNode> result = new ArrayList<>();
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            result.addAll(convertInline(child, marks));
        }
        return result;
    }

    private static String plainText(Node container) {
        ensureDepth(container);
        StringBuilder sb = new StringBuilder();
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) {
                sb.append(text.getLiteral());
            } else {
                sb.append(plainText(child));
            }
        }
        return sb.toString();
    }

    private static void ensureDepth(Node node) {
        int depth = 0;
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (++depth > 64) throw new BizException("Markdown 嵌套层级超出限制");
        }
    }

    private static List<ObjectNode> withMark(List<ObjectNode> marks, ObjectNode mark) {
        List<ObjectNode> next = new ArrayList<>(marks);
        next.add(mark);
        return next;
    }

    private static ObjectNode textNode(String literal, List<ObjectNode> marks) {
        ObjectNode result = object("text");
        result.put("text", literal);
        if (!marks.isEmpty()) {
            ArrayNode markArray = result.putArray("marks");
            marks.forEach(markArray::add);
        }
        return result;
    }

    private static ObjectNode object(String type) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        return node;
    }

    private static String newBlockId() {
        return UUID.randomUUID().toString();
    }
}
