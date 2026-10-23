package com.prismprd.knowledge;

import org.springframework.stereotype.Component;
import java.util.*;
import static com.prismprd.knowledge.KnowledgeTypes.*;

/** Adapted from JChatMind MarkdownParserServiceImpl (MIT); see THIRD_PARTY_NOTICES.md.
 * Heading-based sections retain Markdown, including preamble and headingless files.
 * All state is request-local. Large sections are split into bounded overlapping chunks.
 */
@Component
public class MarkdownSectionSplitter {
    public List<Chunk> split(String markdown) {
        if (markdown.length() > DocumentFileParser.MAX_TEXT_CHARS) throw new com.prismprd.common.BizException("文档过长");
        List<Chunk> result = new ArrayList<>();
        List<String> path = new ArrayList<>();
        String section = "正文";
        StringBuilder content = new StringBuilder();
        String fence = null;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String marker = trimmed.substring(0, 3);
                if (fence == null) fence = marker;
                else if (fence.equals(marker)) fence = null;
            }
            var heading = java.util.regex.Pattern.compile("^(#{1,6})[ \\t]+(.+)$").matcher(line);
            if (fence == null && heading.matches()) {
                append(result, section, content.toString());
                content.setLength(0);
                int level = heading.group(1).length();
                while (path.size() >= level) path.remove(path.size() - 1);
                path.add(heading.group(2).replaceAll("[ \\t]+#+[ \\t]*$", "").strip());
                section = String.join(" / ", path);
                if (section.length() > 512) section = section.substring(0, 512);
            }
            content.append(line).append('\n');
        }
        append(result, section, content.toString());
        if (result.isEmpty()) throw new com.prismprd.common.BizException("文档没有可索引内容");
        return result;
    }

    private void append(List<Chunk> out, String path, String text) {
        text = text.strip();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + 1500);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            out.add(new Chunk(path, text.substring(start, end)));
            if (out.size() > 500) throw new com.prismprd.common.BizException("文档分块不能超过 500 个");
            if (end == text.length()) break;
            start = end - 100;
            if (Character.isLowSurrogate(text.charAt(start))) start++;
        }
    }
}
