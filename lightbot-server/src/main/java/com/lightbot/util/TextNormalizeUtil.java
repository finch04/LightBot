package com.lightbot.util;

/**
 * 文本规范化工具：清理入库/检索内容中的异常换行与空白，避免干扰模型 Markdown 输出
 *
 * @author finch
 * @since 2026-05-26
 */
public final class TextNormalizeUtil {

    private TextNormalizeUtil() {
    }

    /**
     * 分块入库前规范化
     *
     * @param content 原始分块内容
     * @return 规范化后的内容
     */
    public static String normalizeChunkContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        return normalizeInternal(content, true);
    }

    /**
     * 注入模型上下文前规范化（RAG / 工具结果）
     *
     * @param content 分块或文档内容
     * @return 规范化后的内容
     */
    public static String normalizeForPrompt(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        return normalizeInternal(content, false);
    }

    private static String normalizeInternal(String content, boolean unescapeLiterals) {
        String text = content.replace("\r\n", "\n").replace('\r', '\n');

        if (unescapeLiterals) {
            // 部分解析器会把换行存成字面量 \n
            text = text.replace("\\n", "\n").replace("\\t", "\t");
        }

        // 连续 3 行以上空行压缩为 2 行
        text = text.replaceAll("\n{3,}", "\n\n");

        // 去掉每行尾部空白
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i].stripTrailing());
        }
        return sb.toString().strip();
    }
}
