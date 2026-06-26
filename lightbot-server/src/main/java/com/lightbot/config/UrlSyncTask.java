package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private static final int PAGE_SIZE = 100;

    @Scheduled(fixedRate = 60 * 60 * 1000L, initialDelay = 120 * 1000L)
    public void syncUrlDocuments() {
        long now = System.currentTimeMillis();
        int synced = 0;
        int skipped = 0;
        int failed = 0;

        // 1. 分页查询已完成且 metadata 中有 sourceUrl 的文档，避免全量加载 OOM
        int pageNum = 1;
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getStatus, DocumentStatus.COMPLETED)
                .eq(Document::getDeleted, 0)
                .isNotNull(Document::getMetadata)
                .orderByAsc(Document::getId);

        while (true) {
            Page<Document> page = documentService.page(new Page<>(pageNum, PAGE_SIZE), wrapper);
            List<Document> docs = page.getRecords();
            if (docs.isEmpty()) {
                break;
            }

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

            if (docs.size() < PAGE_SIZE) {
                break;
            }
            pageNum++;
        }

        if (synced > 0 || failed > 0) {
            log.info("[URL定时同步] 完成: 同步={}, 跳过={}, 失败={}", synced, skipped, failed);
        }
    }
}
