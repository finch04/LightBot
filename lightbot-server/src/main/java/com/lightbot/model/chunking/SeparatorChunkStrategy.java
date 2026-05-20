package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 严格分隔策略
 * <p>遇分隔符即切分，仅超长片段内部按 token 硬切，不做合并</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class SeparatorChunkStrategy implements ChunkStrategy {

    @Override
    public String getType() {
        return "separator";
    }

    @Override
    public List<String> split(String content, ChunkParams params) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 1. 按分隔符切分
        String[] parts = content.split(params.getDelimiter(), -1);
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            int partTokens = TokenUtil.countTokens(part);

            // 2. 超长片段硬切
            if (partTokens > params.getChunkTokenNum()) {
                result.addAll(TokenUtil.hardSplitByTokens(part, params.getChunkTokenNum()));
            } else {
                result.add(part);
            }
        }

        return result;
    }
}
