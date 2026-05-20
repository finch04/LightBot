package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.dto.ChunkVO;
import com.lightbot.entity.Chunk;
import com.lightbot.enums.ChunkStatus;
import com.lightbot.mapper.ChunkMapper;
import com.lightbot.model.chunking.TokenUtil;
import com.lightbot.service.ChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分块服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl extends ServiceImpl<ChunkMapper, Chunk>
        implements ChunkService {

    /** Markdown标题匹配正则 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    @Override
    public List<String> splitMarkdown(String content, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        // 1. 按Markdown标题拆分段落
        List<String> sections = splitByHeadings(content);

        // 2. 对每个段落按大小切分，超长段落再细分
        for (String section : sections) {
            if (section.length() <= chunkSize) {
                if (!section.isBlank()) {
                    chunks.add(section.trim());
                }
            } else {
                chunks.addAll(splitBySize(section, chunkSize, chunkOverlap));
            }
        }

        return chunks;
    }

    @Override
    public void saveChunk(Long documentId, Long knowledgeId, int index, String content) {
        saveChunk(documentId, knowledgeId, index, content, null);
    }

    @Override
    public void saveChunk(Long documentId, Long knowledgeId, int index, String content, ChunkStatus status) {
        Chunk chunk = new Chunk();
        chunk.setDocumentId(documentId);
        chunk.setKnowledgeId(knowledgeId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setTokenCount(TokenUtil.countTokens(content));
        chunk.setStatus(status);
        save(chunk);
    }

    @Override
    public List<Chunk> listByDocumentId(Long documentId) {
        return list(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getDocumentId, documentId)
                .orderByAsc(Chunk::getChunkIndex));
    }

    @Override
    public List<ChunkVO> listChunkVOsByDocumentId(Long documentId) {
        List<Chunk> chunks = listByDocumentId(documentId);
        return chunks.stream().map(chunk -> {
            ChunkVO vo = new ChunkVO();
            vo.setId(chunk.getId());
            vo.setDocumentId(chunk.getDocumentId());
            vo.setContent(chunk.getContent());
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setTokenCount(chunk.getTokenCount());
            vo.setStatus(chunk.getStatus());
            vo.setMetadata(chunk.getMetadata());
            vo.setCreateTime(chunk.getCreateTime());
            return vo;
        }).toList();
    }

    /**
     * 按Markdown标题（# ## ### 等）拆分段落
     */
    private List<String> splitByHeadings(String content) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);

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
                // 标题前的内容作为独立段落
                if (matcher.start() > 0) {
                    String prefix = content.substring(0, matcher.start());
                    if (!prefix.isBlank()) {
                        sections.add(prefix);
                    }
                }
                foundHeading = true;
            }
            lastHeadingStart = matcher.start();
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
     * 按字符数切分，支持重叠窗口
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
