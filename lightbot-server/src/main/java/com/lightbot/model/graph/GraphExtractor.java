package com.lightbot.model.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.GraphTripleDTO;
import com.lightbot.model.ModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱实体关系抽取器（基于 LLM）
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphExtractor {

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是一个知识图谱实体关系抽取专家。请从给定文本中抽取实体和关系三元组。

            要求：
            1. 实体：提取人名、组织、职位、项目、产品、地点、技术、概念等有明确含义的名词
            2. 关系：提取实体间的语义关系，用简洁的动词或动词短语描述（如"负责"、"隶属于"、"使用"、"包含"）
            3. 实体名称保持原文，不要翻译或缩写
            4. 关系方向要正确：A 做了某事指向 B，则 head=A, tail=B
            5. 如果文本中没有明确的实体关系，返回空数组 []
            6. 实体类型使用以下类别之一：人物、组织、职位、项目、产品、地点、技术、概念、事件、其他

            输出格式（纯 JSON 数组，不要任何额外文字或 markdown 格式）：
            [{"head":"实体A","headType":"类型","headDesc":"描述","relation":"关系","relationDesc":"关系描述","tail":"实体B","tailType":"类型","tailDesc":"描述"}]
            """;

    private static final String ENTITY_EXTRACT_PROMPT = """
            请从以下问题中提取关键实体名称（人名、组织、项目、技术、产品等有明确含义的名词）。
            只输出实体名称的 JSON 数组，不要任何额外文字。

            示例输入："张三负责哪个项目？"
            示例输出：["张三"]

            问题：%s
            """;

    /**
     * 从文本中抽取实体和关系三元组
     *
     * @param content    文本内容
     * @param providerId 模型提供商ID（为空时自动使用第一个可用提供商）
     * @param modelId    指定模型ID（为空时使用 provider 默认模型）
     * @return 三元组列表
     */
    public List<GraphTripleDTO> extract(String content, Long providerId, String modelId) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        // 截取前 2000 字符避免 token 超限
        String truncated = content.length() > 2000 ? content.substring(0, 2000) : content;

        try {
            Long actualProviderId = resolveProviderId(providerId);
            var ctx = modelFactory.getChatModelWithContext(actualProviderId, modelId);

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(EXTRACT_SYSTEM_PROMPT));
            messages.add(new UserMessage("请从以下文本中抽取实体和关系三元组：\n\n" + truncated));

            ChatResponse response = ctx.call(messages);
            String text = response.getResult().getOutput().getText();
            return parseTriples(text);
        } catch (Exception e) {
            log.warn("[图谱抽取] LLM 抽取失败: {}", e.toString(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 从用户问题中提取关键实体名（用于图谱检索）
     *
     * @param question   用户问题
     * @param providerId 模型提供商ID（为空时自动使用第一个可用提供商）
     * @return 实体名列表
     */
    public List<String> extractEntitiesFromQuestion(String question, Long providerId) {
        try {
            Long actualProviderId = resolveProviderId(providerId);
            var ctx = modelFactory.getChatModelWithContext(actualProviderId, null);

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new UserMessage(ENTITY_EXTRACT_PROMPT.formatted(question)));

            ChatResponse response = ctx.call(messages);
            String text = response.getResult().getOutput().getText();
            return parseEntityNames(text);
        } catch (Exception e) {
            log.warn("[图谱抽取] 问题实体提取失败: {}", e.toString(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量抽取：将多个文本块合并为一次 LLM 调用，减少网络往返
     *
     * @param contents   文本内容列表（2-3个chunk）
     * @param providerId 模型提供商ID
     * @param modelId    指定模型ID（为空时使用 provider 默认模型）
     * @return 所有三元组的合并列表
     */
    public List<GraphTripleDTO> extractBatch(List<String> contents, Long providerId, String modelId) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }

        // 合并多个文本块，用分隔符分隔
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            String text = contents.get(i);
            if (text == null || text.isBlank()) continue;
            if (combined.length() > 0) {
                combined.append("\n\n=== 文本段落分隔 ===\n\n");
            }
            // 每个chunk最多2000字符
            combined.append(text.length() > 2000 ? text.substring(0, 2000) : text);
        }

        if (combined.length() == 0) {
            return Collections.emptyList();
        }

        // 总长度截断保护
        String finalText = combined.length() > 6000 ? combined.substring(0, 6000) : combined.toString();

        try {
            Long actualProviderId = resolveProviderId(providerId);
            var ctx = modelFactory.getChatModelWithContext(actualProviderId, modelId);

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(EXTRACT_SYSTEM_PROMPT));
            messages.add(new UserMessage("请从以下文本中抽取实体和关系三元组（文本包含多个段落，请分别抽取）：\n\n" + finalText));

            ChatResponse response = ctx.call(messages);
            String text = response.getResult().getOutput().getText();
            return parseTriples(text);
        } catch (Exception e) {
            log.warn("[图谱抽取] 批量LLM抽取失败, 降级为逐个抽取: {}", e.toString(), e);
            // 降级：逐个抽取
            List<GraphTripleDTO> fallback = new ArrayList<>();
            for (String content : contents) {
                fallback.addAll(extract(content, providerId, modelId));
            }
            return fallback;
        }
    }

    /**
     * 解析提供商ID，为空时自动使用第一个可用提供商
     */
    private Long resolveProviderId(Long providerId) {
        if (providerId != null) {
            return providerId;
        }
        List<Long> availableIds = modelFactory.getAvailableProviderIds();
        if (availableIds.isEmpty()) {
            throw new IllegalStateException("没有可用的模型提供商，请先配置模型");
        }
        return availableIds.get(0);
    }

    /**
     * 解析 LLM 返回的三元组 JSON
     */
    private List<GraphTripleDTO> parseTriples(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 去除可能的 markdown 代码块标记
            String json = text.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
            List<GraphTripleDTO> triples = new ArrayList<>();
            for (Map<String, Object> raw : rawList) {
                GraphTripleDTO dto = new GraphTripleDTO();
                dto.setHead(getString(raw, "head"));
                dto.setHeadType(getString(raw, "headType"));
                dto.setHeadDesc(getString(raw, "headDesc"));
                dto.setRelation(getString(raw, "relation"));
                dto.setRelationDesc(getString(raw, "relationDesc"));
                dto.setTail(getString(raw, "tail"));
                dto.setTailType(getString(raw, "tailType"));
                dto.setTailDesc(getString(raw, "tailDesc"));
                if (dto.getHead() != null && dto.getRelation() != null && dto.getTail() != null) {
                    triples.add(dto);
                }
            }
            return triples;
        } catch (Exception e) {
            log.warn("[图谱抽取] 三元组解析失败: text={}", text.length() > 200 ? text.substring(0, 200) : text, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 LLM 返回的实体名 JSON 数组
     */
    private List<String> parseEntityNames(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String json = text.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[图谱抽取] 实体名解析失败: text={}", text, e);
            return Collections.emptyList();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }
}
