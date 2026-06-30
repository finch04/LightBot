package com.lightbot.util;

import java.util.regex.Pattern;

/**
 * 文本规范化工具：清理入库/检索内容中的异常换行与空白，避免干扰模型 Markdown 输出
 *
 * @author finch
 * @since 2026-05-26
 */
public final class TextNormalizeUtil {

    /** PostgreSQL UTF-8 不支持的 C0 控制字符（0x00-0x1F 中除 \t\n\r 外），使用 Unicode 转义避免源码编码问题 */
    private static final Pattern INVALID_PG_CHARS = Pattern.compile(
            "[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]");
    /** Unicode 特殊空白和格式字符（可能造成存储或展示异常） */
    private static final Pattern UNICODE_SPECIAL = Pattern.compile(
            "[\u200B\u200C\u200D\uFEFF\uFFFE\uFFFF]");

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

    /**
     * PostgreSQL TEXT/JSONB 入库前清理：
     * 1. 移除 NUL（\u0000）及所有 C0 控制字符（除 \t\n\r）
     * 2. 移除 Unicode 特殊空白（零宽字符等）
     * 3. 避免 UTF8 编码报错和展示异常
     *
     * @param content 原始文本
     * @return 可安全入库的文本
     */
    public static String sanitizeForDatabase(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        // 1. 移除 PostgreSQL 不支持的 C0 控制字符（保留 \t=0x09, \n=0x0A, \r=0x0D）
        String cleaned = INVALID_PG_CHARS.matcher(content).replaceAll("");
        // 2. 移除 Unicode 特殊字符（零宽空格、BOM 等）
        cleaned = UNICODE_SPECIAL.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    /**
     * AI 输出入库前完整清理：合并 sanitizeForDatabase + 截断异常长内容
     *
     * @param content   原始文本
     * @param maxLength 最大长度（超过则截断并加 "..."），<=0 表示不限制
     * @return 清理后可安全入库的文本
     */
    public static String sanitizeForAiMessage(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String cleaned = sanitizeForDatabase(content);
        if (maxLength > 0 && cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength - 3) + "...";
        }
        return cleaned;
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
