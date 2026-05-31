package com.lightbot.util;

import com.lightbot.dto.DuplicateCheckResultVO;
import com.lightbot.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文档内容重复检测工具（N-gram Shingling + Jaccard 相似度）
 *
 * @author finch
 * @since 2026-05-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentDuplicateDetectionUtil {

    private final MinioUtil minioUtil;

    private static final int DEFAULT_SHINGLE_SIZE = 3;
    private static final int MAX_SHINGLES = 10000;
    private static final int MIN_CONTENT_LENGTH = 50;

    /**
     * 检查新文档与已有文档的内容重复率
     *
     * @param newContent   新文档的 Markdown 内容
     * @param existingDocs 已有文档列表（需有 markdownPath）
     * @param threshold    相似度阈值
     * @return 检测结果
     */
    public DuplicateCheckResultVO checkDuplicate(String newContent, List<Document> existingDocs, double threshold) {
        if (newContent == null || newContent.length() < MIN_CONTENT_LENGTH) {
            return DuplicateCheckResultVO.noDuplicate(threshold);
        }

        Set<String> newShingles = generateShingles(preprocessText(newContent), DEFAULT_SHINGLE_SIZE);
        if (newShingles.isEmpty()) {
            return DuplicateCheckResultVO.noDuplicate(threshold);
        }

        List<DuplicateCheckResultVO.DuplicateDetail> details = new ArrayList<>();
        double maxSimilarity = 0;
        String mostSimilarDocName = null;

        for (Document doc : existingDocs) {
            if (doc.getMarkdownPath() == null) continue;

            String existingContent = downloadMarkdown(doc.getMarkdownPath());
            if (existingContent == null || existingContent.length() < MIN_CONTENT_LENGTH) continue;

            Set<String> existingShingles = generateShingles(preprocessText(existingContent), DEFAULT_SHINGLE_SIZE);
            double similarity = computeSimilarity(newShingles, existingShingles);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarDocName = doc.getName();
            }

            if (similarity >= threshold) {
                DuplicateCheckResultVO.DuplicateDetail detail = new DuplicateCheckResultVO.DuplicateDetail();
                detail.setDocumentId(doc.getId());
                detail.setDocumentName(doc.getName());
                detail.setSimilarity(Math.round(similarity * 1000.0) / 1000.0);
                details.add(detail);
            }
        }

        DuplicateCheckResultVO result = new DuplicateCheckResultVO();
        result.setHasDuplicate(!details.isEmpty());
        result.setMaxSimilarity(Math.round(maxSimilarity * 1000.0) / 1000.0);
        result.setMostSimilarDocName(mostSimilarDocName);
        result.setThreshold(threshold);
        result.setDetails(details);
        return result;
    }

    /**
     * 文本预处理：小写化、去标点、合并空白
     */
    String preprocessText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 生成 N-gram shingle 集合
     */
    Set<String> generateShingles(String text, int shingleSize) {
        String[] words = text.split("\\s+");
        if (words.length < shingleSize) {
            return words.length > 0 ? Set.of(String.join(" ", words)) : Set.of();
        }

        Set<String> shingles = new LinkedHashSet<>();
        for (int i = 0; i <= words.length - shingleSize && shingles.size() < MAX_SHINGLES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < shingleSize; j++) {
                if (j > 0) sb.append(' ');
                sb.append(words[i + j]);
            }
            shingles.add(sb.toString());
        }
        return shingles;
    }

    /**
     * 计算两个 shingle 集合的 Jaccard 相似度
     */
    double computeSimilarity(Set<String> shingles1, Set<String> shingles2) {
        if (shingles1.isEmpty() && shingles2.isEmpty()) return 1.0;
        if (shingles1.isEmpty() || shingles2.isEmpty()) return 0.0;

        // 用较小的集合做遍历，减少计算量
        Set<String> smaller = shingles1.size() <= shingles2.size() ? shingles1 : shingles2;
        Set<String> larger = shingles1.size() <= shingles2.size() ? shingles2 : shingles1;

        int intersectionSize = 0;
        for (String s : smaller) {
            if (larger.contains(s)) intersectionSize++;
        }

        int unionSize = shingles1.size() + shingles2.size() - intersectionSize;
        return unionSize > 0 ? (double) intersectionSize / unionSize : 0.0;
    }

    /**
     * 从 MinIO 下载 Markdown 内容
     */
    private String downloadMarkdown(String markdownPath) {
        try (InputStream is = minioUtil.download(markdownPath)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[重复检测] 下载Markdown失败: {}", markdownPath, e);
            return null;
        }
    }
}
