package com.lightbot.util;

/**
 * 流式解析正文中的 inline thinking 标签（{@code redacted_thinking} / {@code thinking} / {@code think}），
 * 将标签内内容分流为 reasoning，标签外内容作为正文。
 */
public class InlineThinkingStreamParser {

    private static final String REDACTED_OPEN = "<" + "redacted_thinking>";
    private static final String REDACTED_CLOSE = "</" + "redacted_thinking>";
    private static final String THINKING_OPEN = "<thinking>";
    private static final String THINKING_CLOSE = "</thinking>";
    /** Ollama deepseek-r1 等常用 {@code think} 标签 */
    private static final String THINK_OPEN = "<" + "think>";
    private static final String THINK_CLOSE = "</" + "think>";

    /** 长标签优先，避免 {@code think} 误匹配 {@code thinking} */
    private static final String[] OPEN_TAGS = {REDACTED_OPEN, THINKING_OPEN, THINK_OPEN};
    private static final String[] CLOSE_TAGS = {REDACTED_CLOSE, THINKING_CLOSE, THINK_CLOSE};

    private boolean insideThinking;
    private final StringBuilder pending = new StringBuilder();

    /**
     * 解析结果：reasoningDelta 为思考片段，contentDelta 为正文片段。
     */
    public record ParseResult(String reasoningDelta, String contentDelta) {
        public static ParseResult empty() {
            return new ParseResult("", "");
        }

        public boolean isEmpty() {
            return reasoningDelta.isEmpty() && contentDelta.isEmpty();
        }
    }

    /**
     * 增量解析一个 chunk（跨 chunk 保持状态）。
     *
     * @param chunk 模型输出片段
     * @return 本次可输出的 reasoning / 正文片段
     */
    public ParseResult feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return ParseResult.empty();
        }
        String input = pending.toString() + chunk;
        pending.setLength(0);

        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (!insideThinking) {
                i = scanOutside(input, i, content);
            } else {
                i = scanInside(input, i, reasoning);
            }
        }

        String reasoningOut = reasoning.toString();
        String contentOut = content.toString();
        return trimPartialTail(reasoningOut, contentOut);
    }

    /**
     * 一次性解析完整文本（非流式场景）。
     */
    public static ParseResult parseComplete(String text) {
        if (text == null || text.isEmpty()) {
            return ParseResult.empty();
        }
        InlineThinkingStreamParser parser = new InlineThinkingStreamParser();
        ParseResult result = parser.feed(text);
        if (parser.pending.length() > 0) {
            String tail = parser.pending.toString();
            parser.pending.setLength(0);
            if (parser.insideThinking) {
                result = new ParseResult(result.reasoningDelta() + tail, result.contentDelta());
            } else {
                result = new ParseResult(result.reasoningDelta(), result.contentDelta() + tail);
            }
        }
        return new ParseResult(
                normalizeReasoningText(result.reasoningDelta()),
                normalizeContentText(result.contentDelta()));
    }

    /**
     * 剥离 thinking 标签及内容，仅保留正文。
     */
    public static String stripTags(String text) {
        return parseComplete(text).contentDelta();
    }

    /**
     * 判断文本是否包含 thinking 类标签。
     */
    public static boolean containsThinkingTags(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String tag : OPEN_TAGS) {
            if (text.contains(tag)) {
                return true;
            }
        }
        for (String tag : CLOSE_TAGS) {
            if (text.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 流结束时 flush 挂起的 partial 标签与未闭合 thinking 内容。
     */
    public ParseResult flush() {
        if (pending.isEmpty()) {
            return ParseResult.empty();
        }
        String tail = pending.toString();
        pending.setLength(0);
        if (insideThinking) {
            return new ParseResult(normalizeReasoningText(tail), "");
        }
        return new ParseResult("", normalizeContentText(tail));
    }

    private int scanOutside(String input, int start, StringBuilder content) {
        int i = start;
        while (i < input.length()) {
            int tagStart = input.indexOf('<', i);
            if (tagStart < 0) {
                content.append(input.substring(i));
                return input.length();
            }
            content.append(input, i, tagStart);
            String matchedOpen = matchTagAt(input, tagStart, OPEN_TAGS);
            if (matchedOpen != null) {
                insideThinking = true;
                return skipLeadingNewlines(input, tagStart + matchedOpen.length());
            }
            content.append('<');
            i = tagStart + 1;
        }
        return i;
    }

    private int scanInside(String input, int start, StringBuilder reasoning) {
        int i = start;
        while (i < input.length()) {
            int tagStart = input.indexOf('<', i);
            if (tagStart < 0) {
                reasoning.append(input.substring(i));
                return input.length();
            }
            reasoning.append(input, i, tagStart);
            String matchedClose = matchTagAt(input, tagStart, CLOSE_TAGS);
            if (matchedClose != null) {
                trimTrailingNewlines(reasoning);
                insideThinking = false;
                return tagStart + matchedClose.length();
            }
            // 已在 thinking 块内：重复的 open 标签忽略，仅闭合标签结束
            String matchedOpen = matchTagAt(input, tagStart, OPEN_TAGS);
            if (matchedOpen != null) {
                return tagStart + matchedOpen.length();
            }
            reasoning.append('<');
            i = tagStart + 1;
        }
        return i;
    }

    private ParseResult trimPartialTail(String reasoningOut, String contentOut) {
        String active = insideThinking ? reasoningOut : contentOut;
        if (active.isEmpty()) {
            return new ParseResult(reasoningOut, contentOut);
        }
        int holdFrom = findPartialTagHoldFrom(active, insideThinking);
        if (holdFrom >= active.length()) {
            return new ParseResult(reasoningOut, contentOut);
        }
        String hold = active.substring(holdFrom);
        pending.append(hold);
        if (insideThinking) {
            return new ParseResult(reasoningOut.substring(0, holdFrom), contentOut);
        }
        return new ParseResult(reasoningOut, contentOut.substring(0, holdFrom));
    }

    private static int findPartialTagHoldFrom(String text, boolean insideThinking) {
        int lastLt = text.lastIndexOf('<');
        if (lastLt < 0) {
            return text.length();
        }
        String candidate = text.substring(lastLt);
        String[] tags = insideThinking ? CLOSE_TAGS : OPEN_TAGS;
        if (matchTagAt(candidate, 0, tags) != null) {
            return text.length();
        }
        for (String tag : tags) {
            if (tag.startsWith(candidate) && candidate.length() < tag.length()) {
                return lastLt;
            }
        }
        return text.length();
    }

    private static String matchTagAt(String input, int index, String[] tags) {
        for (String tag : tags) {
            if (input.regionMatches(index, tag, 0, tag.length())) {
                return tag;
            }
        }
        return null;
    }

    /** 开标签后常见换行，跳过 */
    private static int skipLeadingNewlines(String input, int pos) {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    /** 闭标签前常见换行，从 reasoning 末尾去掉 */
    private static void trimTrailingNewlines(StringBuilder sb) {
        while (sb.length() > 0) {
            char c = sb.charAt(sb.length() - 1);
            if (c == '\n' || c == '\r') {
                sb.setLength(sb.length() - 1);
            } else {
                break;
            }
        }
    }

    /** 规范化 reasoning：去掉标签两侧换行、空白行，合并连续空行 */
    public static String normalizeReasoningText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String trimmed = stripEdgeNewlines(text);
        // 去掉仅含空白的行（流式分片常在行间产生空行）
        trimmed = trimmed.replaceAll("(?m)^[ \\t]+$", "");
        // 连续 2 个及以上换行压成 1 个
        trimmed = trimmed.replaceAll("(?:\\r\\n|\\n|\\r){2,}", "\n");
        return stripEdgeNewlines(trimmed);
    }

    /** 规范化正文：去掉标签边界残留换行与多余空行 */
    public static String normalizeContentText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String trimmed = stripEdgeNewlines(text);
        trimmed = trimmed.replaceAll("(?m)^[ \\t]+$", "");
        trimmed = trimmed.replaceAll("(?:\\r\\n|\\n|\\r){2,}", "\n");
        return stripEdgeNewlines(trimmed);
    }

    private static String stripEdgeNewlines(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isNewlineChar(text.charAt(start))) {
            start++;
        }
        while (end > start && isNewlineChar(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isNewlineChar(char c) {
        return c == '\n' || c == '\r';
    }
}
