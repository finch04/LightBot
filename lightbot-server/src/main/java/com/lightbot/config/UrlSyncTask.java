package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Document;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * URL 文档定时同步任务
 * <p>每小时检查一次，同步到期的 URL 文档（根据 syncInterval 配置）</p>
 *
 * @author finch
 * @since 2026-06-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlSyncTask {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;
    private static final long ONE_WEEK_MS = 7 * ONE_DAY_MS;

    /**
     * 每小时执行一次：检查并同步到期的 URL 文档
     */
    @Scheduled(fixedRate = 60 * 60 * 1000L, initialDelay = 120 * 1000L)
    public void syncUrlDocuments() {
        // 1. 查询所有已完成且 metadata 中有 sourceUrl 的文档
        List<Document> docs = documentService.list(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, DocumentStatus.COMPLETED)
                        .eq(Document::getDeleted, 0)
                        .isNotNull(Document::getMetadata));

        if (docs.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int synced = 0;
        int skipped = 0;
        int failed = 0;

        for (Document doc : docs) {
            try {
                Map<String, Object> metaMap = objectMapper.readValue(
                        doc.getMetadata(), new TypeReference<>() {});

                String sourceUrl = (String) metaMap.get("sourceUrl");
                if (sourceUrl == null || sourceUrl.isBlank()) {
                    continue; // 非 URL 文档，跳过
                }

                String syncInterval = (String) metaMap.getOrDefault("syncInterval", "manual");
                if ("manual".equals(syncInterval)) {
                    skipped++;
                    continue; // 手动同步，跳过
                }

                long fetchedAt = metaMap.containsKey("fetchedAt")
                        ? ((Number) metaMap.get("fetchedAt")).longValue() : 0;
                long intervalMs = "weekly".equals(syncInterval) ? ONE_WEEK_MS : ONE_DAY_MS;

                if (now - fetchedAt < intervalMs) {
                    skipped++;
                    continue; // 未到期，跳过
                }

                // 2. 执行同步
                documentService.syncUrlDocument(doc.getId());
                synced++;
            } catch (Exception e) {
                log.warn("[URL定时同步] 同步失败: docId={}, error={}", doc.getId(), e.getMessage());
                failed++;
            }
        }

        if (synced > 0 || failed > 0) {
            log.info("[URL定时同步] 完成: 同步={}, 跳过={}, 失败={}", synced, skipped, failed);
        }
    }
}
