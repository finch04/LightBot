package com.lightbot.service;

import com.lightbot.dto.EvalScoreResult;
import reactor.core.publisher.Flux;

/**
 * 评测对话服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalChatService {

    /**
     * 调用提示词模板，返回模型响应
     *
     * @param modelConfig    模型配置（JSON）
     * @param promptTemplate 提示词模板
     * @param variables      变量值（JSON）
     * @return 模型响应内容
     */
    String callPrompt(String modelConfig, String promptTemplate, String variables);

    /**
     * 流式调用提示词模板，返回SSE事件流
     *
     * @param modelConfig    模型配置（JSON）
     * @param promptTemplate 提示词模板
     * @param variables      变量值（JSON）
     * @return SSE事件流
     */
    Flux<String> callPromptStream(String modelConfig, String promptTemplate, String variables);

    /**
     * 调用评测器，返回评分结果
     *
     * @param modelConfig    模型配置（JSON）
     * @param promptTemplate 评测提示词模板
     * @param variables      变量值（JSON）
     * @return 评分结果
     */
    EvalScoreResult callEvaluator(String modelConfig, String promptTemplate, String variables);

    /**
     * 替换模板中的变量占位符
     *
     * @param template     包含占位符的模板
     * @param variablesJson 变量值（JSON）
     * @return 替换后的字符串
     */
    String replaceVariables(String template, String variablesJson);
}
