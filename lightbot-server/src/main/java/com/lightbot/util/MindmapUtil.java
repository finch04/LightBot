package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Document;
import com.lightbot.model.ModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 思维导图生成工具类
 * <p>基于文件列表（名称+类型）生成思维导图JSON结构，最多20个文件</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindmapUtil {

    private static final int MAX_FILES = 20;

    private static final String MINDMAP_SYSTEM_PROMPT = """
            你是一个思维导图生成助手。根据提供的文件列表生成JSON格式的思维导图结构。
            严格规则：
            - 只输出JSON，不要任何其他文字、解释或markdown标记
            - 每个文件名在思维导图中只能出现一次，绝不重复
            - 层级为2-4层，叶子节点的content必须是文件名
            - 根节点content必须是知识库名称
            - 根据文件名和类型进行逻辑分组，选择最主要的一个分类维度
            - 使用emoji图标增强可读性
            - children数组中每个子节点必须有content字段，可选children字段
            - 叶子节点的children必须是空数组[]
            """;

    private static final String MINDMAP_USER_TEMPLATE = """
            请为知识库"%s"生成思维导图结构。

            文件列表（共%d个文件）：
            %s

            重要提醒：
            1. 每个文件名只能在思维导图中出现一次
            2. 不要让同一个文件出现在多个分类下
            3. 为每个文件选择最合适的唯一分类

            输出格式（只输出JSON）：
            {"content": "知识库名称", "children": [...]}
            """;

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    /**
     * 基于文件列表生成思维导图
     *
     * @param knowledgeName 知识库名称
     * @param documents     文档列表（已过滤为COMPLETED状态）
     * @param providerId    模型提供商ID
     * @return 解析后的思维导图JSON对象
     */
    public Object generateFromFiles(String knowledgeName, List<Document> documents, Long providerId) {
        // 1. 限制文件数量
        List<Document> limitedDocs = documents.size() > MAX_FILES
                ? documents.subList(0, MAX_FILES)
                : documents;

        // 2. 构建文件列表文本
        StringBuilder fileList = new StringBuilder();
        for (Document doc : limitedDocs) {
            fileList.append("- ").append(doc.getName())
                    .append(" (").append(doc.getFileType()).append(")\n");
        }

        // 3. 构建提示词
        String userMessage = String.format(MINDMAP_USER_TEMPLATE,
                knowledgeName, limitedDocs.size(), fileList.toString());

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(MINDMAP_SYSTEM_PROMPT));
        messages.add(new UserMessage(userMessage));

        // 4. 调用AI生成
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        ChatResponse response = chatModel.call(new Prompt(messages));
        String json = response.getResult().getOutput().getText().trim();

        // 5. 清理并解析JSON
        json = cleanJsonResponse(json);
        return parseJson(json);
    }

    /**
     * 清理AI返回的JSON（去除markdown代码块标记）
     */
    public String cleanJsonResponse(String json) {
        if (json == null) {
            return null;
        }
        json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        return json.trim();
    }

    /**
     * 将JSON字符串解析为Object（Map/List），供Jackson直接序列化
     */
    public Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[MindmapUtil] JSON解析失败: {}", e.getMessage());
            throw new RuntimeException("思维导图数据解析失败", e);
        }
    }
}
