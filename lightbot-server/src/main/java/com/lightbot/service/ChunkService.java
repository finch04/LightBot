package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.Chunk;
import com.lightbot.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分块服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChunkService extends ServiceImpl<ChunkMapper, Chunk> {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    /**
     * Markdown 分块：优先按标题拆分，超长段落再按 token 数切分
     *
     * @param content      Markdown 内容
     * @param chunkSize    分块大小（字符数）
     * @param chunkOverlap 分块重叠（字符数）
     * @return 分块列表
     */
    public List<String> splitMarkdown(String content, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        // 1. 按标题拆分段落
        List<String> sections = splitByHeadings(content);

        // 2. 对每个段落按大小切分
        for (String section : sections) {
            if (section.length() <= chunkSize) {
                if (!section.isBlank()) {
                    chunks.add(section.trim());
                }
            } else {
                // 超长段落按字符数切分
                chunks.addAll(splitBySize(section, chunkSize, chunkOverlap));
            }
        }

        return chunks;
    }

    /**
     * 保存分块到数据库
     */
    public void saveChunk(Long documentId, Long knowledgeId, int index, String content) {
        Chunk chunk = new Chunk();
        chunk.setDocumentId(documentId);
        chunk.setKnowledgeId(knowledgeId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setTokenCount((int) (content.length() * 1.2));
        save(chunk);
    }

    /**
     * 按 Markdown 标题拆分段落
     */
    private List<String> splitByHeadings(String content) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);

        int lastEnd = 0;
        int lastHeadingStart = 0;
        boolean foundHeading = false;

        while (matcher.find()) {
            if (foundHeading) {
                // 保存上一个标题段落
                String section = content.substring(lastHeadingStart, matcher.start());
                if (!section.isBlank()) {
                    sections.add(section);
                }
            } else {
                // 标题前的内容
                if (matcher.start() > 0) {
                    String prefix = content.substring(0, matcher.start());
                    if (!prefix.isBlank()) {
                        sections.add(prefix);
                    }
                }
                foundHeading = true;
            }
            lastHeadingStart = matcher.start();
            lastEnd = matcher.end();
        }

        // 最后一个段落
        if (foundHeading) {
            String lastSection = content.substring(lastHeadingStart);
            if (!lastSection.isBlank()) {
                sections.add(lastSection);
            }
        } else if (!content.isBlank()) {
            sections.add(content);
        }

        return sections;
    }

    /**
     * 按字符数切分，支持重叠
     */
    private List<String> splitBySize(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            String chunk = text.substring(start, end);
            if (!chunk.isBlank()) {
                result.add(chunk.trim());
            }
            start += size - overlap;
            if (start >= text.length()) {
                break;
            }
        }
        return result;
    }
}
