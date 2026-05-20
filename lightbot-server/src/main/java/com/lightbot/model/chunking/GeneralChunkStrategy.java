package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用分块策略
 * <p>递归分隔符切分 + token 合并 + 重叠 + 超长硬切</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class GeneralChunkStrategy implements ChunkStrategy {

    private static final int MAX_OVERLAP_TOKENS = 200;

    @Override
    public String getType() {
        return "general";
    }

    @Override
    public List<String> split(String content, ChunkParams params) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 1. 按分隔符切分为 sections
        String[] sections = content.split(params.getDelimiter(), -1);
        List<String> merged = new ArrayList<>();

        // 2. 按 token 数合并 sections
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String section : sections) {
            int sectionTokens = TokenUtil.countTokens(section);

            // 超长 section 硬切
            if (sectionTokens > params.getChunkTokenNum()) {
                // 先把当前缓冲区输出
                if (!current.isEmpty()) {
                    merged.add(current.toString());
                    current.setLength(0);
                    currentTokens = 0;
                }
                merged.addAll(TokenUtil.hardSplitByTokens(section, params.getChunkTokenNum()));
                continue;
            }

            // 合并后是否超限
            if (currentTokens + sectionTokens > params.getChunkTokenNum() && !current.isEmpty()) {
                merged.add(current.toString());
                current.setLength(0);
                currentTokens = 0;
            }

            if (!current.isEmpty()) {
                current.append(params.getDelimiter());
            }
            current.append(section);
            currentTokens += sectionTokens;
        }

        // 3. 输出最后一段
        if (!current.isEmpty()) {
            merged.add(current.toString());
        }

        // 4. 按百分比重叠
        if (params.getOverlappedPercent() > 0 && merged.size() > 1) {
            return addOverlap(merged, params.getOverlappedPercent(), params.getDelimiter());
        }

        return merged;
    }

    /**
     * 为相邻分块添加重叠
     *
     * @param chunks             原始分块列表
     * @param overlappedPercent  重叠百分比（0-99）
     * @param delimiter          分隔符
     * @return 添加重叠后的分块列表
     */
    private List<String> addOverlap(List<String> chunks, int overlappedPercent, String delimiter) {
        if (overlappedPercent <= 0 || overlappedPercent >= 100 || chunks.size() <= 1) {
            return chunks;
        }

        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String curr = chunks.get(i);

            int prevTokens = TokenUtil.countTokens(prev);
            int overlapTokens = Math.min(prevTokens * overlappedPercent / 100, MAX_OVERLAP_TOKENS);

            if (overlapTokens > 0) {
                // 从上一段尾部截取重叠内容
                String overlapText = getTailTokens(prev, overlapTokens, delimiter);
                result.add(overlapText + delimiter + curr);
            } else {
                result.add(curr);
            }
        }

        return result;
    }

    /**
     * 获取文本尾部约 N 个 token 的内容
     */
    private String getTailTokens(String text, int tokenCount, String delimiter) {
        String[] parts = text.split(delimiter, -1);
        List<String> tail = new ArrayList<>();
        int accumulated = 0;

        for (int i = parts.length - 1; i >= 0; i--) {
            int tokens = TokenUtil.countTokens(parts[i]);
            if (accumulated + tokens > tokenCount && !tail.isEmpty()) {
                break;
            }
            tail.add(0, parts[i]);
            accumulated += tokens;
        }

        return String.join(delimiter, tail);
    }
}
