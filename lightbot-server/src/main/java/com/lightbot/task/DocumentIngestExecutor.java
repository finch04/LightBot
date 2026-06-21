package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Task;
import com.lightbot.service.DocumentService;
import com.lightbot.service.TaskService;
import com.lightbot.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文档入库任务执行器
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Component("documentIngestExecutor")
@RequiredArgsConstructor
public class DocumentIngestExecutor implements TaskExecutor {

    private final DocumentService documentService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;

    @Override
    public String execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long documentId = payload.get("documentId").asLong();
        String embeddingJson = payload.get("embeddingJson").toString();

        log.info("[文档入库执行器] 开始, taskId={}, documentId={}", task.getId(), documentId);

        documentService.processDocumentWithProgress(documentId, embeddingJson, (progress, message) -> {
            // 1. 检查取消请求（Redis信号，O(1)）
            if (redisUtil.hasCancelSignal(task.getId())) {
                log.info("[文档入库执行器] 收到取消请求, taskId={}", task.getId());
                throw new RuntimeException("任务已被用户取消");
            }
            // 2. 更新进度
            taskService.updateProgress(task.getId(), progress, message);
        });

        // 读取处理后的文档信息作为结果（包含重复检测警告）
        var doc = documentService.getById(documentId);
        if (doc != null) {
            String result = String.format("入库完成, documentId=%d, chunkCount=%d, tokenCount=%d",
                    documentId, doc.getChunkCount(), doc.getTokenCount());
            if (doc.getDuplicateRate() != null && doc.getDuplicateRate() > 0) {
                result += String.format(", 重复率=%.1f%%", doc.getDuplicateRate() * 100);
            }
            return result;
        }
        return "入库完成, documentId=" + documentId;
    }
}
