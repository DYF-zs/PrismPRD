package com.prismprd.prd.version;

import com.prismprd.common.BizException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.commonmark.node.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Local-only text PDF renderer. Never interprets HTML or fetches image/link resources. */
@Component
public class VersionPdfRenderer {
    private final Semaphore slots = new Semaphore(2);
    private static final int MAX_CHARS = 500_000, MAX_PAGES = 250;
    private static final float MARGIN = 48, TOP = 784, BOTTOM = 58;
    private static final Color INK = new Color(30,41,59), MUTED = new Color(100,116,139), GREEN = new Color(22,101,52);
    private static final Color PALE = new Color(243,246,248), BORDER = new Color(211,220,225);
    private static final Parser PARSER = Parser.builder().extensions(List.of(TablesExtension.create())).build();

    public byte[] render(PrdVersion version) {
        String markdown = Objects.requireNonNullElse(version.getMarkdownSnapshot(), "");
        if (markdown.length() > MAX_CHARS) throw new BizException(413, "PDF 正文不能超过 500,000 字符，请拆分文档");
        if (!slots.tryAcquire()) throw new BizException(503, "PDF 导出任务较多，请稍后重试");
        try (var layout = new Layout()) {
            layout.doc.getDocumentInformation().setTitle(Objects.requireNonNullElse(version.getTitleSnapshot(), "PRD"));
            layout.doc.getDocumentInformation().setCreator("PrismPRD");
            layout.doc.getDocumentCatalog().setLanguage("zh-CN");
            layout.paragraph("PRISM / PRODUCT REQUIREMENTS", 9, 0, true, GREEN, false);
            layout.paragraph(Objects.requireNonNullElse(version.getTitleSnapshot(), "PRD"), 23, 0, true, INK, false);
            String created = version.getCreatedAt() == null ? "" : "  |  " + version.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx"));
            layout.paragraph("固定版本 v" + version.getVersionNo() + created, 9, 0, false, MUTED, false);
            layout.rule();
            if (markdown.isBlank()) layout.paragraph("（此版本暂无正文）", 11, 0, false, MUTED, false);
            else layout.blocks(PARSER.parse(markdown), 0);
            if (layout.unsupported) layout.paragraph("字符说明：字体未覆盖的字符已用 [U+编码] 标示，未执行或下载正文中的外部内容。", 8, 0, false, MUTED, false);
            return layout.finish();
        } catch (IOException e) { throw new BizException(503, "PDF 生成失败，请检查字体资源或稍后重试"); }
        finally { slots.release(); }
    }

    private static byte[] resource(String name) throws IOException {
        try (var stream = VersionPdfRenderer.class.getResourceAsStream("/fonts/" + name)) {
            if (stream == null) throw new IOException("Missing PDF font " + name);
            return stream.readAllBytes();
        }
    }
    private record FontData(byte[] text, byte[] emoji) {}
    private static volatile FontData fontData;
    private static synchronized FontData fonts() throws IOException {
        // Bytes are shared; parsed fonts and documents are request-local. Failed loads can retry.
        if (fontData == null) fontData = new FontData(resource("NotoSansSC-Regular.ttf"), resource("NotoEmoji-Regular.ttf"));
        return fontData;
    }

    private record Glyph(String text, PDFont font, float width) {}
    private class Layout implements AutoCloseable {
        final PDDocument doc = new PDDocument();
        final PDFont text, emoji, mono = new PDType1Font(Standard14Fonts.FontName.COURIER);
        final Map<Integer, List<Glyph>> glyphs = new HashMap<>();
        PDPageContentStream stream;
        float y;
        boolean unsupported;
        int nodes;
        final long deadline = System.nanoTime() + 30_000_000_000L;

        Layout() throws IOException {
            try {
                var data = fonts();
                text = PDType0Font.load(doc, new ByteArrayInputStream(data.text()), true);
                emoji = PDType0Font.load(doc, new ByteArrayInputStream(data.emoji()), true);
                page();
            } catch (IOException | RuntimeException | Error e) { doc.close(); throw e; }
        }
        float width() { return PDRectangle.A4.getWidth() - MARGIN * 2; }
        void page() throws IOException {
            if (doc.getNumberOfPages() >= MAX_PAGES) throw new BizException(413, "PDF 超过 250 页，请拆分文档后导出");
            if (stream != null) stream.close();
            var page = new PDPage(PDRectangle.A4); doc.addPage(page);
            stream = new PDPageContentStream(doc, page); y = TOP;
        }
        void ensure(float height) throws IOException {
            if (System.nanoTime() > deadline) throw new BizException(503, "PDF 排版耗时过长，请拆分文档后重试");
            if (y - height < BOTTOM) page();
        }
        List<Glyph> glyph(int cp) throws IOException {
            if (cp == '\t') return List.of(new Glyph("    ", text, text.getStringWidth("    ") / 1000));
            if (cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D) return List.of();
            if (Character.isISOControl(cp)) cp = ' ';
            var cached = glyphs.get(cp);
            if (cached != null) return cached;
            String value = new String(Character.toChars(cp));
            for (PDFont font : List.of(text, emoji)) {
                try {
                    font.encode(value);
                    var result = List.of(new Glyph(value, font, font.getStringWidth(value) / 1000)); glyphs.put(cp, result); return result;
                } catch (IllegalArgumentException ignored) { /* next local font */ }
            }
            unsupported = true;
            String replacement = "[U+" + Integer.toHexString(cp).toUpperCase(Locale.ROOT) + "]";
            List<Glyph> result = new ArrayList<>();
            for (int c : replacement.codePoints().toArray()) result.add(new Glyph(String.valueOf((char)c), text, text.getStringWidth(String.valueOf((char)c)) / 1000));
            glyphs.put(cp, result); return result;
        }
        List<List<Glyph>> wrap(String value, float size, float available, boolean code) throws IOException {
            List<List<Glyph>> lines = new ArrayList<>(); List<Glyph> line = new ArrayList<>(); float used = 0;
            for (int cp : value.replace("\r\n", "\n").replace('\r','\n').codePoints().toArray()) {
                if (cp == '\n') { lines.add(line); line = new ArrayList<>(); used = 0; continue; }
                var items = code && cp >= 32 && cp <= 126 ? List.of(new Glyph(String.valueOf((char)cp), mono, .6f)) : glyph(cp);
                for (Glyph g : items) {
                    if (used + g.width * size > available && !line.isEmpty()) {
                        int space = -1;
                        if (!code) for (int i = line.size() - 1; i > line.size() / 2; i--) if (line.get(i).text.equals(" ")) { space = i; break; }
                        if (space >= 0) {
                            lines.add(new ArrayList<>(line.subList(0, space)));
                            line = new ArrayList<>(line.subList(space + 1, line.size()));
                            used = 0; for (var tail : line) used += tail.width * size;
                        } else { lines.add(line); line = new ArrayList<>(); used = 0; }
                    }
                    line.add(g); used += g.width * size;
                }
            }
            lines.add(line); return lines;
        }
        void draw(List<Glyph> line, float x, float baseline, float size, boolean bold, Color color) throws IOException {
            stream.setNonStrokingColor(color); stream.setStrokingColor(color); stream.setLineWidth(.18f);
            stream.beginText(); stream.setRenderingMode(bold ? RenderingMode.FILL_STROKE : RenderingMode.FILL);
            stream.newLineAtOffset(x, baseline);
            PDFont current = null;
            StringBuilder run = new StringBuilder();
            for (Glyph g : line) {
                if (current != g.font) {
                    if (!run.isEmpty()) { stream.showText(run.toString()); run.setLength(0); }
                    current = g.font; stream.setFont(current, size);
                }
                run.append(g.text);
            }
            if (!run.isEmpty()) stream.showText(run.toString());
            stream.setRenderingMode(RenderingMode.FILL); stream.endText();
        }
        void paragraph(String value, float size, float indent, boolean bold, Color color, boolean code) throws IOException {
            float leading = size * 1.65f;
            for (var line : wrap(value, size, width() - indent - (code ? 16 : 0), code)) {
                ensure(leading + 2);
                if (code) fill(MARGIN + indent - 5, y - leading, width() - indent + 5, leading + 1, PALE);
                draw(line, MARGIN + indent + (code ? 4 : 0), y - size, size, bold, color); y -= leading;
            }
            y -= code ? 3 : 7;
        }
        void fill(float x, float bottom, float w, float h, Color color) throws IOException {
            stream.setNonStrokingColor(color); stream.addRect(x, bottom, w, h); stream.fill();
        }
        void rule() throws IOException {
            ensure(14); stream.setStrokingColor(BORDER); stream.setLineWidth(.6f);
            stream.moveTo(MARGIN, y - 2); stream.lineTo(MARGIN + width(), y - 2); stream.stroke(); y -= 16;
        }
        void blocks(Node parent, int depth) throws IOException {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) block(n, depth);
        }
        void block(Node n, int depth) throws IOException {
            if (depth > 30) throw new BizException(413, "PDF 文档层级过深，请简化嵌套结构");
            if (++nodes > 50_000) throw new BizException(413, "PDF 文档节点过多，请拆分文档");
            float indent = Math.min(depth * 12, 120);
            if (n instanceof Heading h) {
                ensure(65); y -= 7;
                paragraph(inline(n), Math.max(11, 20 - h.getLevel() * 2), indent, true, GREEN, false);
            } else if (n instanceof Paragraph) paragraph(inline(n), 10.5f, indent, false, INK, false);
            else if (n instanceof FencedCodeBlock c) {
                if (!c.getInfo().isBlank()) paragraph(c.getInfo(), 8, indent, true, MUTED, false);
                paragraph(c.getLiteral().stripTrailing(), 9, indent, false, INK, true);
            } else if (n instanceof IndentedCodeBlock c) paragraph(c.getLiteral().stripTrailing(), 9, indent, false, INK, true);
            else if (n instanceof BulletList || n instanceof OrderedList) {
                boolean orderedList = n instanceof OrderedList;
                int number = n instanceof OrderedList ordered ? ordered.getMarkerStartNumber() : 0;
                for (Node item = n.getFirstChild(); item != null; item = item.getNext()) {
                    String prefix = orderedList ? (number++) + ". " : "• ";
                    Node first = item.getFirstChild();
                    if (first instanceof Paragraph) {
                        paragraph(prefix + inline(first), 10.5f, indent + 10, false, INK, false);
                        for (Node child = first.getNext(); child != null; child = child.getNext()) block(child, depth + 1);
                    } else blocks(item, depth + 1);
                }
            } else if (n instanceof BlockQuote) {
                paragraph("引用", 8, indent, true, MUTED, false); blocks(n, depth + 1);
            } else if (n instanceof ThematicBreak) rule();
            else if (n instanceof TableBlock) table(n, indent);
            else if (n instanceof HtmlBlock html) paragraph(html.getLiteral(), 9, indent, false, MUTED, true);
            else blocks(n, depth + 1);
        }
        String inline(Node node) {
            StringBuilder result = new StringBuilder();
            // Explicit stack avoids recursion for adversarial inline emphasis/link nesting.
            Deque<Object> stack = new ArrayDeque<>();
            for (Node child = node.getLastChild(); child != null; child = child.getPrevious()) stack.push(child);
            while (!stack.isEmpty()) {
                Object item = stack.pop();
                if (item instanceof String tail) { result.append(tail); continue; }
                Node child = (Node)item;
                if (child instanceof Text t) result.append(t.getLiteral());
                else if (child instanceof Code c) result.append(c.getLiteral());
                else if (child instanceof HardLineBreak) result.append('\n');
                else if (child instanceof SoftLineBreak) result.append(' ');
                else if (child instanceof HtmlInline h) result.append(h.getLiteral());
                else {
                    if (child instanceof Link link) stack.push(" (" + link.getDestination() + ")");
                    if (child instanceof Image image) { result.append("[图片："); stack.push(" | " + image.getDestination() + "]"); }
                    for (Node inner = child.getLastChild(); inner != null; inner = inner.getPrevious()) stack.push(inner);
                }
            }
            return result.toString();
        }
        void table(Node block, float indent) throws IOException {
            List<List<String>> rows = new ArrayList<>();
            for (Node section = block.getFirstChild(); section != null; section = section.getNext())
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    List<String> cells = new ArrayList<>();
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) cells.add(inline(cell));
                    rows.add(cells);
                }
            if (rows.isEmpty()) return;
            int columns = rows.stream().mapToInt(List::size).max().orElse(1);
            if (columns > 6) {
                paragraph("宽表（" + columns + " 列，逐行展开）", 9, indent, true, MUTED, false);
                for (int r = 1; r < rows.size(); r++) {
                    paragraph("第 " + r + " 行", 10, indent, true, GREEN, false);
                    for (int c = 0; c < columns; c++) paragraph(cell(rows.get(0), c) + "：" + cell(rows.get(r), c), 10, indent + 10, false, INK, false);
                }
                if (rows.size() == 1) for (String header : rows.get(0)) paragraph(header, 10, indent, true, INK, false);
                return;
            }
            float columnWidth = (width() - indent) / Math.max(1, columns), size = columns >= 5 ? 9 : 10, leading = size * 1.6f;
            var header = rowLines(rows.get(0), columns, size, columnWidth);
            int headerCount = header.stream().mapToInt(List::size).max().orElse(1);
            // A gigantic header must not monopolize each new page.
            if (headerCount * leading > 160) {
                paragraph("表格（长表头，逐行展开）", 9, indent, true, MUTED, false);
                for (int r = 0; r < rows.size(); r++) for (int c = 0; c < columns; c++) paragraph((r == 0 ? "表头" : "第 " + r + " 行") + " / " + (c + 1) + "：" + cell(rows.get(r),c), 10, indent, false, INK, false);
                return;
            }
            ensure(headerCount * leading + leading + 20);
            rowSegment(header, 0, headerCount, columns, columnWidth, size, leading, indent, true);
            for (int r = 1; r < rows.size(); r++) {
                var cells = rowLines(rows.get(r), columns, size, columnWidth);
                int lines = cells.stream().mapToInt(List::size).max().orElse(1), offset = 0;
                while (offset < lines) {
                    int capacity = (int)((y - BOTTOM - 12) / leading);
                    if (capacity < 1) { page(); rowSegment(header, 0, headerCount, columns, columnWidth, size, leading, indent, true); continue; }
                    int count = Math.min(capacity, lines - offset);
                    rowSegment(cells, offset, count, columns, columnWidth, size, leading, indent, false); offset += count;
                    if (offset < lines) { page(); rowSegment(header, 0, headerCount, columns, columnWidth, size, leading, indent, true); }
                }
            }
            y -= 12;
        }
        String cell(List<String> row, int c) { return c < row.size() ? row.get(c) : ""; }
        List<List<List<Glyph>>> rowLines(List<String> row, int columns, float size, float columnWidth) throws IOException {
            List<List<List<Glyph>>> result = new ArrayList<>();
            for (int c = 0; c < columns; c++) result.add(wrap(cell(row,c), size, columnWidth - 14, false));
            return result;
        }
        void rowSegment(List<List<List<Glyph>>> cells, int offset, int count, int columns, float columnWidth, float size, float leading, float indent, boolean header) throws IOException {
            float height = count * leading + 12;
            if (header) fill(MARGIN + indent, y - height, columns * columnWidth, height, PALE);
            stream.setStrokingColor(BORDER); stream.setLineWidth(.5f);
            for (int c = 0; c < columns; c++) {
                float x = MARGIN + indent + c * columnWidth;
                stream.addRect(x, y - height, columnWidth, height); stream.stroke();
                for (int i = 0; i < count && i + offset < cells.get(c).size(); i++)
                    draw(cells.get(c).get(i + offset), x + 7, y - 6 - size - i * leading, size, header, INK);
            }
            y -= height;
        }
        byte[] finish() throws IOException {
            stream.close(); stream = null;
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                stream = new PDPageContentStream(doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true);
                draw(wrap("PrismPRD  |  固定版本快照", 8, width(), false).get(0), MARGIN, 32, 8, false, MUTED);
                draw(wrap((i + 1) + " / " + doc.getNumberOfPages(), 8, 80, false).get(0), PDRectangle.A4.getWidth() - 90, 32, 8, false, MUTED);
                stream.close(); stream = null;
            }
            var bytes = new ByteArrayOutputStream(); doc.save(bytes); return bytes.toByteArray();
        }
        public void close() throws IOException { try { if (stream != null) stream.close(); } finally { doc.close(); } }
    }
}
