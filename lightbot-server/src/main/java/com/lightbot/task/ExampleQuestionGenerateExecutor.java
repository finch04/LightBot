package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Task;
import com.lightbot.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 示例问题生成任务执行器
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component("exampleQuestionGenerateExecutor")
@RequiredArgsConstructor
public class ExampleQuestionGenerateExecutor implements TaskExecutor {

    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long knowledgeId = payload.get("knowledgeId").asLong();
        Long documentId = payload.get("documentId").asLong();

        log.info("[示例问题执行器] 开始, taskId={}, knowledgeId={}, documentId={}", task.getId(), knowledgeId, documentId);

        knowledgeService.generateExampleQuestions(knowledgeId, documentId);

        return "示例问题生成完成, knowledgeId=%d, documentId=%d".formatted(knowledgeId, documentId);
    }
}
