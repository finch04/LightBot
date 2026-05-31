package com.lightbot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.QaPairCreateDTO;
import com.lightbot.entity.Document;
import com.lightbot.entity.Task;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.DocumentService;
import com.lightbot.service.QaPairService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 问答对AI生成任务执行器
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Component("qaPairGenerateExecutor")
@RequiredArgsConstructor
public class QaPairGenerateExecutor implements TaskExecutor {

    private final QaPairService qaPairService;
    private final DocumentService documentService;
    private final TaskService taskService;
    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一个知识库问答对提取专家。根据文档内容，提取用户可能会问的具体问题及其标准答案。
            要求：
            1. 问题要具体明确，10-30个字
            2. 答案要准确完整，基于文档内容
            3. 返回JSON数组格式，不要有其他内容
            4. 每个元素包含 question 和 answer 字段

            示例：
            [{"question": "如何重置密码？", "answer": "1. 点击登录页的「忘记密码」\n2. 输入注册邮箱\n3. 查收重置邮件并点击链接\n4. 设置新密码"}]
            """;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long knowledgeId = payload.get("knowledgeId").asLong();
        int count = payload.get("count").asInt(10);
        Long providerId = payload.has("providerId") && payload.get("providerId").asLong(0) > 0
                ? payload.get("providerId").asLong() : null;
        String modelId = payload.has("modelId") ? payload.get("modelId").asText("") : "";
        if (modelId.isBlank()) modelId = null;
        String providerName = payload.has("providerName") ? payload.get("providerName").asText("") : "";

        String modelInfo = providerName.isBlank() ? String.valueOf(providerId) : providerName + (modelId != null ? "/" + modelId : "");
        log.info("[问答对生成执行器] 开始, taskId={}, knowledgeId={}, count={}, model={}", task.getId(), knowledgeId, count, modelInfo);

        var tracker = new TaskProgressTracker(taskService, task.getId())
                .phases("加载文档", "AI生成", "保存结果");

        // 1. 加载已完成文档
        tracker.nextPhase("正在加载知识库文档...");
        checkCancelled(task.getId());

        var documents = documentService.list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeId, knowledgeId)
                .eq(Document::getStatus, com.lightbot.enums.DocumentStatus.COMPLETED));

        if (documents.isEmpty()) {
            throw new RuntimeException("知识库中没有已完成的文档，无法生成问答对");
        }

        // 2. 取第一个文档的内容作为生成依据
        tracker.nextPhase("正在准备文档内容...");
        checkCancelled(task.getId());

        Document doc = documents.get(0);
        String content = documentService.readDocumentContent(doc.getId());
        if (content == null || content.isBlank()) {
            throw new RuntimeException("文档内容为空: documentId=" + doc.getId() + ", name=" + doc.getName());
        }
        String truncated = content.length() > 5000 ? content.substring(0, 5000) : content;
        String docName = doc.getName();

        // 3. 调用AI生成问答对
        tracker.nextPhase("正在调用AI生成问答对 (0/" + count + ")...");
        checkCancelled(task.getId());

        // 解析可用的 providerId（优先使用传入的，否则使用系统默认）
        Long actualProviderId = resolveProviderId(providerId);
        var ctx = modelFactory.getChatModelWithContext(actualProviderId, modelId);

        String userPrompt = String.format("文档名称：%s\n\n请从以下文档内容中提取 %d 个问答对。\n\n文档内容：\n%s",
                docName, count, truncated);

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));

        ChatResponse response = com.lightbot.util.LlmTraceContext.callWithoutTrace(
                () -> ctx.call(messages));
        String reply = response.getResult().getOutput().getText();

        // 4. 解析AI返回的JSON
        tracker.nextPhase("正在保存问答对...");
        checkCancelled(task.getId());

        List<QaPairCreateDTO> qaPairs = parseQaPairs(reply);
        if (qaPairs.isEmpty()) {
            throw new RuntimeException("AI未能生成有效的问答对");
        }

        // 限制数量
        if (qaPairs.size() > count) {
            qaPairs = qaPairs.subList(0, count);
        }

        // 5. 批量保存并触发向量化（内部调用，跳过权限校验）
        int saved = qaPairService.batchImportInternal(knowledgeId, qaPairs);

        log.info("[问答对生成执行器] 完成, taskId={}, saved={}", task.getId(), saved);
        return "生成完成，共生成 " + saved + " 条问答对";
    }

    /**
     * 从AI回复中解析问答对JSON
     */
    private List<QaPairCreateDTO> parseQaPairs(String reply) {
        if (reply == null) return List.of();
        try {
            // 处理可能的markdown代码块包裹
            String json = reply.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            // 解析JSON数组
            List<JsonNode> nodes = objectMapper.readValue(json.strip(), new TypeReference<>() {});
            List<QaPairCreateDTO> result = new ArrayList<>();
            for (JsonNode node : nodes) {
                if (node.has("question") && node.has("answer")) {
                    QaPairCreateDTO dto = new QaPairCreateDTO();
                    dto.setQuestion(node.get("question").asText().trim());
                    dto.setAnswer(node.get("answer").asText().trim());
                    if (!dto.getQuestion().isBlank() && !dto.getAnswer().isBlank()) {
                        result.add(dto);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[问答对生成执行器] 解析AI回复失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void checkCancelled(Long taskId) {
        Task latest = taskService.getById(taskId);
        if (latest != null && latest.getCancelRequested() == 1) {
            throw new RuntimeException("任务已被用户取消");
        }
    }

    /**
     * 解析providerId，为空时使用系统默认AI配置
     */
    private Long resolveProviderId(Long providerId) {
        if (providerId != null) {
            return providerId;
        }
        var defaultConfig = systemConfigService.getDefaultAiConfig();
        if (defaultConfig.getProviderId() != null) {
            return defaultConfig.getProviderId();
        }
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new RuntimeException("未配置AI模型提供商，请先在系统设置中配置");
        }
        return providers.get(0);
    }
}
