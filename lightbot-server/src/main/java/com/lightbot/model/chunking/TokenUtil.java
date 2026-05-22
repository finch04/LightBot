package com.lightbot.model.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 计数工具
 * <p>中英混合文本的近似 token 计算：中文按字计数，英文按单词计数</p>
 *
 * @author finch
 * @since 2026-05-20
 */
public final class TokenUtil {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]+|[一-鿿]");

    /** 最小分块 token 数，低于此值的碎片会被过滤 */
    public static final int MIN_CHUNK_TOKENS = 30;

    private TokenUtil() {
    }

    /**
     * 计算文本的近似 token 数
     *
     * @param text 文本
     * @return token 数
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 按 token 上限切分文本，返回 token 级别的片段列表
     * <p>不做合并，仅用于超长文本的硬切兜底</p>
     *
     * @param text          文本
     * @param maxTokens     每段最大 token 数
     * @return 切分后的文本片段
     */
    public static List<String> hardSplitByTokens(String text, int maxTokens) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        StringBuilder current = new StringBuilder();
        int tokenCount = 0;
        int lastEnd = 0;

        while (matcher.find()) {
            // 追加匹配前的非token字符（空格、标点等）
            if (matcher.start() > lastEnd) {
                current.append(text, lastEnd, matcher.start());
            }
            current.append(matcher.group());
            lastEnd = matcher.end();
            tokenCount++;

            if (tokenCount >= maxTokens) {
                result.add(current.toString().trim());
                current.setLength(0);
                tokenCount = 0;
            }
        }

        // 追加尾部剩余非token字符
        if (lastEnd < text.length()) {
            current.append(text, lastEnd, text.length());
        }
        if (!current.isEmpty()) {
            String tail = current.toString().trim();
            if (!tail.isEmpty()) {
                result.add(tail);
            }
        }

        return result;
    }

    /**
     * 过滤低于最小 token 数的碎片分块
     *
     * @param chunks 原始分块列表
     * @return 过滤后的分块列表
     */
    public static List<String> filterByMinTokens(List<String> chunks) {
        return chunks.stream()
                .filter(c -> countTokens(c) >= MIN_CHUNK_TOKENS)
                .toList();
    }
}
