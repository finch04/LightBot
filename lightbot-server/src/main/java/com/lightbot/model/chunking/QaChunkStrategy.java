package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QA 问答对分块策略
 * <p>识别 Q:/A:、问题/答案、编号问题等模式，保持问答对完整性</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class QaChunkStrategy implements ChunkStrategy {

    /** QA 模式：Q:/A:、问题/答案、问/答 */
    private static final Pattern QA_PATTERN = Pattern.compile(
            "^(?:Q[：:]|问题[：:]|问[：:]|\\*\\*Q[：:]\\*\\*|\\d+[.、])\\s*",
            Pattern.MULTILINE
    );

    /** 答案开始模式 */
    private static final Pattern A_PATTERN = Pattern.compile(
            "^(?:A[：:]|答案[：:]|答[：:]|\\*\\*A[：:]\\*\\*)\\s*",
            Pattern.MULTILINE
    );

    @Override
    public String getType() {
        return "qa";
    }

    @Override
    public List<String> split(String content, ChunkParams params) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        // 1. 按问答对解析
        List<QaPair> pairs = parseQaPairs(content);

        // 2. 如果没有识别到问答对，回退到通用分块
        if (pairs.isEmpty()) {
            log.info("[QaChunkStrategy] 未识别到问答对模式，回退到通用分块");
            return fallbackSplit(content, params);
        }

        // 3. 处理每个问答对
        for (QaPair pair : pairs) {
            String qaText = pair.toString();
            int tokens = TokenUtil.countTokens(qaText);

            // 3.1 问答对超过限制，拆分答案部分
            if (tokens > params.getChunkTokenNum()) {
                result.addAll(splitLongQa(pair, params));
            } else {
                result.add(qaText);
            }
        }

        // 4. 合并过短的问答对
        return mergeShortChunks(result, params);
    }

    /**
     * 解析问答对
     */
    private List<QaPair> parseQaPairs(String content) {
        List<QaPair> pairs = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentQ = new StringBuilder();
        StringBuilder currentA = new StringBuilder();
        boolean inQuestion = false;
        boolean inAnswer = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (inQuestion) currentQ.append("\n");
                if (inAnswer) currentA.append("\n");
                continue;
            }

            // 检测问题开始
            if (isQuestionStart(trimmed)) {
                // 保存之前的问答对
                if (currentQ.length() > 0) {
                    pairs.add(new QaPair(currentQ.toString().trim(), currentA.toString().trim()));
                    currentQ.setLength(0);
                    currentA.setLength(0);
                }
                currentQ.append(trimmed).append("\n");
                inQuestion = true;
                inAnswer = false;
            }
            // 检测答案开始
            else if (isAnswerStart(trimmed)) {
                currentA.append(trimmed).append("\n");
                inQuestion = false;
                inAnswer = true;
            }
            // 继续当前部分
            else {
                if (inAnswer) {
                    currentA.append(trimmed).append("\n");
                } else if (inQuestion) {
                    currentQ.append(trimmed).append("\n");
                }
            }
        }

        // 保存最后一个问答对
        if (currentQ.length() > 0) {
            pairs.add(new QaPair(currentQ.toString().trim(), currentA.toString().trim()));
        }

        return pairs;
    }

    private boolean isQuestionStart(String line) {
        return line.matches("^(?i)(Q[：:]|问题[：:]|问[：:]|\\*\\*Q[：:]\\*\\*|\\d+[.、]).*");
    }

    private boolean isAnswerStart(String line) {
        return line.matches("^(?i)(A[：:]|答案[：:]|答[：:]|\\*\\*A[：:]\\*\\*).*");
    }

    /**
     * 拆分过长的问答对
     */
    private List<String> splitLongQa(QaPair pair, ChunkParams params) {
        List<String> result = new ArrayList<>();
        String question = pair.question();
        int questionTokens = TokenUtil.countTokens(question);

        // 如果问题本身就超长，硬切问题
        if (questionTokens > params.getChunkTokenNum()) {
            result.addAll(TokenUtil.hardSplitByTokens(question, params.getChunkTokenNum()));
        }

        // 拆分答案，保留问题作为前缀
        int remainingTokens = params.getChunkTokenNum() - Math.min(questionTokens, 100);
        List<String> answerChunks = TokenUtil.hardSplitByTokens(pair.answer(), Math.max(remainingTokens, 100));

        for (String chunk : answerChunks) {
            result.add(question + "\n" + chunk);
        }

        return result;
    }

    /**
     * 合并过短的分块
     */
    private List<String> mergeShortChunks(List<String> chunks, ChunkParams params) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String chunk : chunks) {
            int currentTokens = TokenUtil.countTokens(current.toString());
            int chunkTokens = TokenUtil.countTokens(chunk);

            if (currentTokens + chunkTokens <= params.getChunkTokenNum()) {
                if (current.length() > 0) current.append("\n\n");
                current.append(chunk);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString());
                }
                current = new StringBuilder(chunk);
            }
        }

        if (current.length() > 0) {
            result.add(current.toString());
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
     * QA 问答对记录
     */
    private record QaPair(String question, String answer) {
        @Override
        public String toString() {
            if (answer.isEmpty()) {
                return question;
            }
            return question + "\n" + answer;
        }
    }
}
