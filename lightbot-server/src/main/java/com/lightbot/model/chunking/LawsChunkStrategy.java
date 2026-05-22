package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律法规分块策略
 * <p>按编/章/节/条款结构切分，保持法律条文的上下文完整性</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class LawsChunkStrategy implements ChunkStrategy {

    /** 法律结构标题模式：第X编/章/节/条/款 */
    private static final Pattern LAW_HEADING = Pattern.compile(
            "^第[一二三四五六七八九十百千零\\d]+[编章节条款]\\s*.*"
    );

    /** 条文编号模式：第X条 */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千零\\d]+条\\s*.*"
    );

    /** 款号模式：（一）（二）等 */
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "^（[一二三四五六七八九十百千零\\d]+）\\s*.*"
    );

    @Override
    public String getType() {
        return "laws";
    }

    @Override
    public List<String> split(String content, ChunkParams params) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 1. 解析法律结构
        List<LegalSection> sections = parseLegalStructure(content);

        // 2. 如果没有识别到法律结构，回退到通用分块
        if (sections.isEmpty() || sections.stream().noneMatch(s -> ARTICLE_PATTERN.matcher(s.heading()).matches())) {
            log.info("[LawsChunkStrategy] 未识别到法律条文结构，回退到通用分块");
            return fallbackSplit(content, params);
        }

        // 3. 按条文分块，保留上下文
        return TokenUtil.filterByMinTokens(chunkByArticles(sections, params));
    }

    /**
     * 解析法律结构
     */
    private List<LegalSection> parseLegalStructure(String content) {
        List<LegalSection> sections = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentHeading = new StringBuilder();
        StringBuilder currentBody = new StringBuilder();
        String lastChapter = "";
        String lastSection = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                currentBody.append("\n");
                continue;
            }

            // 检测法律标题
            if (LAW_HEADING.matcher(trimmed).matches()) {
                // 保存之前的段落
                if (currentHeading.length() > 0 || currentBody.length() > 0) {
                    sections.add(new LegalSection(
                            currentHeading.toString().trim(),
                            currentBody.toString().trim(),
                            lastChapter,
                            lastSection
                    ));
                    currentHeading.setLength(0);
                    currentBody.setLength(0);
                }

                // 更新上下文
                if (trimmed.startsWith("第") && trimmed.contains("编")) {
                    lastChapter = trimmed;
                    lastSection = "";
                } else if (trimmed.startsWith("第") && trimmed.contains("章")) {
                    lastChapter = trimmed;
                    lastSection = "";
                } else if (trimmed.startsWith("第") && trimmed.contains("节")) {
                    lastSection = trimmed;
                }

                currentHeading.append(trimmed).append("\n");
            } else {
                currentBody.append(trimmed).append("\n");
            }
        }

        // 保存最后一段
        if (currentHeading.length() > 0 || currentBody.length() > 0) {
            sections.add(new LegalSection(
                    currentHeading.toString().trim(),
                    currentBody.toString().trim(),
                    lastChapter,
                    lastSection
            ));
        }

        return sections;
    }

    /**
     * 按条文分块
     */
    private List<String> chunkByArticles(List<LegalSection> sections, ChunkParams params) {
        List<String> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String contextPrefix = "";

        for (LegalSection section : sections) {
            String fullText = section.fullText();
            int tokens = TokenUtil.countTokens(fullText);

            // 更新上下文前缀（编/章/节）
            if (!section.chapter().isEmpty()) {
                contextPrefix = section.chapter();
                if (!section.section().isEmpty()) {
                    contextPrefix += " " + section.section();
                }
            }

            // 如果是条文，添加上下文前缀
            String textWithPrefix = fullText;
            if (ARTICLE_PATTERN.matcher(section.heading()).matches() && !contextPrefix.isEmpty()) {
                textWithPrefix = contextPrefix + "\n" + fullText;
            }

            int currentTokens = TokenUtil.countTokens(currentChunk.toString());

            // 合并短条文
            if (currentTokens + tokens <= params.getChunkTokenNum()) {
                if (currentChunk.length() > 0) currentChunk.append("\n\n");
                currentChunk.append(textWithPrefix);
            } else {
                // 保存当前分块
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }

                // 超长条文按句子拆分
                if (tokens > params.getChunkTokenNum()) {
                    result.addAll(splitLongArticle(textWithPrefix, contextPrefix, params));
                } else {
                    currentChunk.append(textWithPrefix);
                }
            }
        }

        // 保存最后一个分块
        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString());
        }

        return result;
    }

    /**
     * 拆分超长条文
     */
    private List<String> splitLongArticle(String article, String contextPrefix, ChunkParams params) {
        List<String> result = new ArrayList<>();

        // 按句号、分号拆分
        String[] sentences = article.split("(?<=[。；！？])");

        StringBuilder currentChunk = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;

            int currentTokens = TokenUtil.countTokens(currentChunk.toString());
            int sentenceTokens = TokenUtil.countTokens(sentence);

            if (currentTokens + sentenceTokens <= params.getChunkTokenNum()) {
                currentChunk.append(sentence);
            } else {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }
                currentChunk.append(sentence);
            }
        }

        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString());
        }

        return result;
    }

    /**
     * 回退到通用分块
     */
    private List<String> fallbackSplit(String content, ChunkParams params) {
        GeneralChunkStrategy general = new GeneralChunkStrategy();
        return general.split(content, params);
    }

    /**
     * 法律段落记录
     */
    private record LegalSection(String heading, String body, String chapter, String section) {
        String fullText() {
            if (body.isEmpty()) return heading;
            return heading + "\n" + body;
        }
    }
}
