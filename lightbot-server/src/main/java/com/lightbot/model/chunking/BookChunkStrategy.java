package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 书籍分块策略
 * <p>识别 Markdown 标题和中文章节标题，按层级拆分，合并短段落</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class BookChunkStrategy implements ChunkStrategy {

    /** Markdown 标题：# ~ ###### */
    private static final Pattern MD_HEADING = Pattern.compile("^#{1,6}\\s+.+");

    /** 中文标题模式：第X章/第X节/第X部分 */
    private static final Pattern CN_HEADING = Pattern.compile("^第[一二三四五六七八九十百千零\\d]+[章节部分篇回].*");

    @Override
    public String getType() {
        return "book";
    }

    @Override
    public List<String> split(String content, ChunkParams params) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 1. 按标题拆分段落
        List<Section> sections = parseSections(content, params.getDelimiter());

        // 2. 合并短段落直到达到 token 上限
        return TokenUtil.filterByMinTokens(mergeSections(sections, params));
    }

    /**
     * 按标题拆分段落
     */
    private List<Section> parseSections(String content, String delimiter) {
        List<Section> sections = new ArrayList<>();
        String[] lines = content.split(delimiter, -1);

        String currentHeading = "";
        StringBuilder currentBody = new StringBuilder();

        for (String line : lines) {
            if (isHeading(line)) {
                // 保存上一段
                if (!currentBody.isEmpty() || !currentHeading.isEmpty()) {
                    sections.add(new Section(currentHeading, currentBody.toString().trim()));
                }
                currentHeading = line;
                currentBody.setLength(0);
            } else {
                if (!currentBody.isEmpty()) {
                    currentBody.append(delimiter);
                }
                currentBody.append(line);
            }
        }

        // 保存最后一段
        if (!currentBody.isEmpty() || !currentHeading.isEmpty()) {
            sections.add(new Section(currentHeading, currentBody.toString().trim()));
        }

        return sections;
    }

    /**
     * 判断是否为标题行
     */
    private boolean isHeading(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.trim();
        return MD_HEADING.matcher(trimmed).matches() || CN_HEADING.matcher(trimmed).matches();
    }

    /**
     * 合并短段落，保持标题上下文
     */
    private List<String> mergeSections(List<Section> sections, ChunkParams params) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        String lastHeading = "";

        for (Section section : sections) {
            // 构建带标题上下文的内容
            String headingContext = "";
            if (!section.heading.isEmpty() && !section.heading.equals(lastHeading)) {
                headingContext = section.heading + params.getDelimiter();
                lastHeading = section.heading;
            }

            String sectionText = section.heading.isEmpty()
                    ? section.body
                    : section.heading + params.getDelimiter() + section.body;
            int sectionTokens = TokenUtil.countTokens(sectionText);

            // 超长 section：先输出 buffer，再硬切当前 section
            if (sectionTokens > params.getChunkTokenNum()) {
                if (!buffer.isEmpty()) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                    bufferTokens = 0;
                }
                // 对超长 section 使用 general 策略硬切，但保留标题前缀
                String prefix = section.heading.isEmpty() ? "" : section.heading + params.getDelimiter();
                List<String> subChunks = TokenUtil.hardSplitByTokens(section.body,
                        params.getChunkTokenNum() - TokenUtil.countTokens(prefix));
                for (String sub : subChunks) {
                    result.add(prefix.isEmpty() ? sub : prefix + sub);
                }
                continue;
            }

            // 合并后是否超限
            if (bufferTokens + sectionTokens > params.getChunkTokenNum() && !buffer.isEmpty()) {
                result.add(buffer.toString().trim());
                buffer.setLength(0);
                bufferTokens = 0;
            }

            // 添加标题上下文到新段落开头
            if (buffer.isEmpty() && !headingContext.isEmpty() && !section.heading.isEmpty()) {
                buffer.append(headingContext);
                bufferTokens += TokenUtil.countTokens(headingContext);
            }

            if (!buffer.isEmpty()) {
                buffer.append(params.getDelimiter());
            }
            buffer.append(sectionText);
            bufferTokens += sectionTokens;
        }

        if (!buffer.isEmpty()) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    /**
     * 文档段落（标题 + 正文）
     */
    private record Section(String heading, String body) {
    }
}
