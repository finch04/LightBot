package com.lightbot.util;

import com.lightbot.dto.EvalEvaluatorExampleVO;

import java.util.*;

/**
 * 内置示例评估器模板定义
 * <p>提供 4 个常见评估场景的示例评估器，帮助用户快速创建评估器并学习评估 Prompt 的编写方式</p>
 *
 * @author finch
 * @since 2026-06-19
 */
public final class EvalEvaluatorExampleTemplates {

    private EvalEvaluatorExampleTemplates() {}

    // ========== 公开 API ==========

    /**
     * 获取所有示例列表（不含详情，用于前端展示）
     */
    public static List<EvalEvaluatorExampleVO> listExamples() {
        return List.of(
                EvalEvaluatorExampleVO.builder()
                        .key("text_similarity")
                        .name("文本相似度评估器")
                        .description("评估两段文本的语义相似度，输出 0-1 分数。适用于翻译质量、改写一致性等场景")
                        .tags(List.of("相似度", "语义匹配"))
                        .build(),
                EvalEvaluatorExampleVO.builder()
                        .key("answer_completeness")
                        .name("回答完整性评估器")
                        .description("评估 AI 回答是否完整覆盖了参考答案的关键信息点，适用于 RAG 问答和知识库场景")
                        .tags(List.of("完整性", "信息覆盖", "RAG"))
                        .build(),
                EvalEvaluatorExampleVO.builder()
                        .key("code_quality")
                        .name("代码质量评估器")
                        .description("从可读性、效率和最佳实践三个维度评估代码质量，适用于代码生成场景")
                        .tags(List.of("可读性", "效率", "最佳实践"))
                        .build(),
                EvalEvaluatorExampleVO.builder()
                        .key("sentiment_analysis")
                        .name("情感分析评估器")
                        .description("分析文本的情感倾向，输出 -1（非常负面）到 1（非常正面）的情感分数")
                        .tags(List.of("情感", "正负向", "舆情"))
                        .build()
        );
    }

    /**
     * 根据 key 获取示例的完整数据（用于创建评估器 + 首个版本）
     *
     * @param key 示例标识
     * @return 示例数据，不存在返回 null
     */
    public static ExampleEvaluatorData getExampleData(String key) {
        return switch (key) {
            case "text_similarity" -> buildTextSimilarity();
            case "answer_completeness" -> buildAnswerCompleteness();
            case "code_quality" -> buildCodeQuality();
            case "sentiment_analysis" -> buildSentimentAnalysis();
            default -> null;
        };
    }

    // ========== 数据结构 ==========

    /**
     * 示例评估器完整数据
     *
     * @param name         评估器名称
     * @param description  评估器描述
     * @param prompt       评估 Prompt 模板
     * @param variables    变量定义（JSON 数组）
     * @param modelConfig  模型配置（JSON）
     */
    public record ExampleEvaluatorData(
            String name,
            String description,
            String prompt,
            String variables,
            String modelConfig
    ) {}

    // ========== 示例构建 ==========

    private static ExampleEvaluatorData buildTextSimilarity() {
        return new ExampleEvaluatorData(
                "文本相似度评估器",
                "评估两段文本的语义相似度，输出 0-1 分数",
                "请评估以下两个文本的语义相似度。\n\n"
                        + "评分标准：\n"
                        + "- 1.0：完全相同或语义一致\n"
                        + "- 0.8：高度相似，仅有细微差异\n"
                        + "- 0.6：大部分相似，有少量不同\n"
                        + "- 0.4：部分相似，有明显差异\n"
                        + "- 0.2：略有相关，大部分不同\n"
                        + "- 0.0：完全不相关\n\n"
                        + "参考文本：{{reference_output}}\n\n"
                        + "待评估文本：{{actual_output}}\n\n"
                        + "请输出 JSON 格式：{\"score\": 分数, \"reason\": \"评分理由\"}",
                "[\"reference_output\",\"actual_output\"]",
                "{\"temperature\":0.1}"
        );
    }

    private static ExampleEvaluatorData buildAnswerCompleteness() {
        return new ExampleEvaluatorData(
                "回答完整性评估器",
                "评估 AI 回答是否完整覆盖了参考答案的关键信息点",
                "请评估以下 AI 回答是否完整覆盖了参考答案中的关键信息。\n\n"
                        + "评分标准：\n"
                        + "- 1.0：完整覆盖所有关键信息点\n"
                        + "- 0.8：覆盖大部分关键信息，遗漏次要细节\n"
                        + "- 0.6：覆盖约一半关键信息\n"
                        + "- 0.4：仅覆盖少量关键信息\n"
                        + "- 0.2：几乎未覆盖关键信息\n"
                        + "- 0.0：完全未覆盖或回答错误\n\n"
                        + "用户问题：{{input}}\n\n"
                        + "参考答案：{{expected_output}}\n\n"
                        + "AI 回答：{{actual_output}}\n\n"
                        + "请输出 JSON 格式：{\"score\": 分数, \"reason\": \"评分理由，列出覆盖和遗漏的信息点\"}",
                "[\"input\",\"expected_output\",\"actual_output\"]",
                "{\"temperature\":0.1}"
        );
    }

    private static ExampleEvaluatorData buildCodeQuality() {
        return new ExampleEvaluatorData(
                "代码质量评估器",
                "从可读性、效率和最佳实践三个维度评估代码质量",
                "请评估以下代码的质量。\n\n"
                        + "评分维度：\n"
                        + "1. 可读性（命名、结构、注释）\n"
                        + "2. 效率（时间/空间复杂度、是否有明显性能问题）\n"
                        + "3. 最佳实践（错误处理、边界条件、安全性）\n\n"
                        + "待评估代码：\n{{code}}\n\n"
                        + "评分要求：\n"
                        + "- 综合三个维度给出 0-1 的总分\n"
                        + "- 指出具体问题和改进建议\n\n"
                        + "请输出 JSON 格式：{\"score\": 分数, \"reason\": \"评分理由，包含各维度分析\"}",
                "[\"code\"]",
                "{\"temperature\":0.2}"
        );
    }

    private static ExampleEvaluatorData buildSentimentAnalysis() {
        return new ExampleEvaluatorData(
                "情感分析评估器",
                "分析文本的情感倾向，输出 -1 到 1 的情感分数",
                "请分析以下文本的情感倾向。\n\n"
                        + "评分标准：\n"
                        + "- 1.0：非常正面（赞美、满意、推荐）\n"
                        + "- 0.5：较为正面\n"
                        + "- 0.0：中性（客观陈述、无明显情感）\n"
                        + "- -0.5：较为负面\n"
                        + "- -1.0：非常负面（批评、愤怒、失望）\n\n"
                        + "待分析文本：{{text}}\n\n"
                        + "请输出 JSON 格式：{\"score\": 分数, \"reason\": \"情感分析理由，引用文本中的关键情感词汇\"}",
                "[\"text\"]",
                "{\"temperature\":0.1}"
        );
    }
}
