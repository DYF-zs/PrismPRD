package com.prismprd.knowledge;

import com.prismprd.common.BizException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static com.prismprd.knowledge.KnowledgeTypes.*;

/** Bounded file ingestion. Documents are data: external XML resources and macros are never executed. */
@Component
public class DocumentFileParser {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 500_000;
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("上传文件不能为空");
        if (file.getSize() > MAX_FILE_BYTES) throw new BizException(413, "文件不能超过 10 MiB");
        try (var in = file.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new BizException(413, "文件不能超过 10 MiB");
            return bytes;
        } catch (IOException e) { throw new BizException("无法读取上传文件"); }
    }

    public String filename(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.chars().anyMatch(c -> c < 32)
                || value.contains("/") || value.contains("\\") || value.contains(":"))
            throw new BizException("文件名不合法");
        return value;
    }

    public ParsedFile parse(String originalName, byte[] bytes, boolean reference) {
        String name = filename(originalName);
        if (bytes.length == 0) throw new BizException("上传文件不能为空");
        if (bytes.length > MAX_FILE_BYTES) throw new BizException(413, "文件不能超过 10 MiB");
        String ext = extension(name), mime, text;
        try {
            switch (ext) {
                case "md", "markdown", "txt" -> {
                    mime = ext.equals("txt") ? "text/plain" : "text/markdown";
                    text = decode(bytes);
                    if (ext.equals("txt")) text = plainTextMarkdown(text);
                }
                case "docx" -> {
                    mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    text = docx(bytes);
                }
                case "pdf" -> {
                    if (!reference) throw new BizException("PDF 仅支持作为知识库参考资料，不导入为可编辑正文");
                    mime = "application/pdf";
                    text = pdf(bytes);
                }
                default -> throw new BizException("支持 Markdown、TXT、DOCX；知识库还支持文本型 PDF");
            }
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("文件损坏、格式不匹配或无法解析"); }
        if (text.isBlank()) throw new BizException("未提取到正文；扫描型 PDF 需要先进行 OCR");
        if (text.length() > MAX_TEXT_CHARS) throw new BizException("提取正文不能超过 500,000 字符");
        return new ParsedFile(name, mime, text.strip());
    }

    public String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        int start = bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf ? 3 : 0;
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        if (text.indexOf('\0') >= 0) throw new BizException("文本含二进制内容，请使用 UTF-8 文本文件");
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String plainTextMarkdown(String text) {
        return text.replaceAll("(?m)([\\\\`*_{}\\[\\]<>#!|~])", "\\\\$1");
    }

    private String docx(byte[] bytes) throws Exception {
        byte[] xml = null;
        byte[] stylesXml = null;
        long expanded = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 2000) throw new BizException("DOCX 压缩包条目过多");
                if (entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) throw new BizException("不支持带宏的文档");
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 20 * 1024 * 1024) throw new BizException("DOCX 解压后内容过大");
                    if (entry.getName().equals("word/document.xml") || entry.getName().equals("word/styles.xml")) output.write(buffer, 0, n);
                }
                if (entry.getName().equals("word/document.xml")) {
                    if (xml != null) throw new BizException("DOCX 包含重复正文");
                    xml = output.toByteArray();
                }
                if (entry.getName().equals("word/styles.xml")) {
                    if (stylesXml != null) throw new BizException("DOCX 包含重复样式定义");
                    stylesXml = output.toByteArray();
                }
            }
        }
        if (xml == null) throw new BizException("不是有效的 DOCX 文档");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute("jdk.xml.maxElementDepth", "128");
        var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        Map<String,Integer> headingStyles = new HashMap<>();
        if (stylesXml != null) {
            var styleRoot = factory.newDocumentBuilder().parse(new ByteArrayInputStream(stylesXml));
            var definitions = styleRoot.getElementsByTagNameNS(W,"style");
            for (int i=0;i<definitions.getLength();i++) {
                var style = (Element) definitions.item(i);
                int level = outlineLevel(style);
                var names = style.getElementsByTagNameNS(W,"name");
                if (level == 0 && names.getLength()>0) level = namedHeadingLevel(((Element)names.item(0)).getAttributeNS(W,"val"));
                if (level>0) headingStyles.put(style.getAttributeNS(W,"styleId"),level);
            }
        }
        var bodies = root.getElementsByTagNameNS(W, "body");
        if (bodies.getLength() != 1) throw new BizException("DOCX 缺少正文");
        StringBuilder out = new StringBuilder();
        for (Node node = bodies.item(0).getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                if ("p".equals(element.getLocalName())) out.append(paragraph(element,headingStyles)).append("\n\n");
                else if ("tbl".equals(element.getLocalName())) table(element, out);
            }
            if (out.length() > MAX_TEXT_CHARS) throw new BizException("提取正文过长");
        }
        return out.toString();
    }

    private String paragraph(Element p, Map<String,Integer> headingStyles) {
        String prefix = "";
        int level = outlineLevel(p);
        var styles = p.getElementsByTagNameNS(W, "pStyle");
        if (styles.getLength() > 0) {
            String style = ((Element) styles.item(0)).getAttributeNS(W, "val");
            if (level == 0) level = headingStyles.getOrDefault(style,namedHeadingLevel(style));
        }
        if (level>0) prefix = "#".repeat(level)+" ";
        if (prefix.isEmpty() && p.getElementsByTagNameNS(W, "numPr").getLength() > 0) prefix = "- ";
        return prefix + plainTextMarkdown(elementText(p));
    }

    private int namedHeadingLevel(String name) {
        var match = java.util.regex.Pattern.compile("(?i)(heading|标题)\\s*([1-6])").matcher(name);
        return match.matches()?Integer.parseInt(match.group(2)):0;
    }

    private int outlineLevel(Element element) {
        var levels = element.getElementsByTagNameNS(W,"outlineLvl");
        if (levels.getLength()==0) return 0;
        String value = ((Element)levels.item(0)).getAttributeNS(W,"val");
        return value.matches("[0-5]")?Integer.parseInt(value)+1:0;
    }

    private String elementText(Element element) {
        StringBuilder text = new StringBuilder();
        var all = element.getElementsByTagNameNS(W, "*");
        for (int i = 0; i < all.getLength(); i++) {
            var node = all.item(i);
            if ("t".equals(node.getLocalName())) text.append(node.getTextContent());
            else if ("tab".equals(node.getLocalName())) text.append(' ');
            else if ("br".equals(node.getLocalName())) text.append(' ');
        }
        return text.toString();
    }

    private void table(Element table, StringBuilder out) {
        var rows = table.getElementsByTagNameNS(W, "tr");
        for (int i = 0; i < rows.getLength(); i++) {
            var cells = ((Element) rows.item(i)).getElementsByTagNameNS(W, "tc");
            out.append('|');
            for (int j = 0; j < cells.getLength(); j++) out.append(' ').append(plainTextMarkdown(elementText((Element) cells.item(j)))).append(" |");
            out.append('\n');
            if (i == 0) out.append("| --- ".repeat(cells.getLength())).append("|\n");
        }
        out.append('\n');
    }

    private String pdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new BizException("PDF 文件头不匹配");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) throw new BizException("PDF 不允许提取正文");
            if (document.getNumberOfPages() > 200) throw new BizException("PDF 不能超过 200 页");
            var stripper = new PDFTextStripper() {
                private int count;
                @Override protected void writeString(String text, java.util.List<org.apache.pdfbox.text.TextPosition> positions) throws IOException {
                    count += text.length();
                    if (count > MAX_TEXT_CHARS) throw new BizException("PDF 正文过长");
                    super.writeString(text, positions);
                }
            };
            return stripper.getText(document);
        }
    }
}
