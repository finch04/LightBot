package com.lightbot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Document;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.service.DocumentService;
import com.lightbot.service.TaskService;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.OcrUtil;
import com.lightbot.util.TikaUtil;
import com.lightbot.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档上传任务执行器：MinIO上传 + Tika解析 + OCR + Markdown转换
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Component("documentUploadExecutor")
@RequiredArgsConstructor
public class DocumentUploadExecutor implements TaskExecutor {

    private final DocumentService documentService;
    private final TaskService taskService;
    private final MinioUtil minioUtil;
    private final TikaUtil tikaUtil;
    private final OcrUtil ocrUtil;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(Task task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload());
        Long documentId = payload.get("documentId").asLong();
        String tempPath = payload.get("tempPath").asText();
        boolean ocrEnabled = payload.has("ocrEnabled") && payload.get("ocrEnabled").asBoolean();

        Document doc = documentService.getById(documentId);
        if (doc == null) {
            log.warn("[文档上传执行器] 文档不存在, documentId={}", documentId);
            return;
        }

        log.info("[文档上传执行器] 开始, taskId={}, documentId={}, tempPath={}", task.getId(), documentId, tempPath);

        try {
            // 1. 上传原始文件到 MinIO
            taskService.updateProgress(task.getId(), 10, "正在上传文件...");
            Path temp = Path.of(tempPath);
            long fileSize = Files.size(temp);
            try (InputStream is = Files.newInputStream(temp)) {
                minioUtil.upload(is, doc.getFilePath(), fileSize, "application/octet-stream");
            }
            log.info("[文档上传执行器] MinIO上传完成, documentId={}", documentId);

            // 2. Tika 解析为 Markdown
            taskService.updateProgress(task.getId(), 30, "正在解析文档...");
            String markdownContent = null;
            try (InputStream is = Files.newInputStream(temp)) {
                markdownContent = tikaUtil.parseToMarkdown(is, doc.getName());
            }

            // 3. OCR 增强
            if (ocrEnabled && isContentTooShort(markdownContent, doc.getFileType())) {
                taskService.updateProgress(task.getId(), 50, "正在OCR识别...");
                try (InputStream is = Files.newInputStream(temp)) {
                    String ocrContent = tryOcr(is, doc.getFileType());
                    if (ocrContent != null && !ocrContent.isBlank()) {
                        markdownContent = mergeOcrContent(markdownContent, ocrContent);
                        log.info("[文档上传执行器] OCR识别完成, documentId={}, ocrLength={}", documentId, ocrContent.length());
                    }
                }
            }

            // 4. 上传 Markdown 到 MinIO
            taskService.updateProgress(task.getId(), 80, "正在保存解析结果...");
            if (markdownContent != null) {
                String markdownPath = generateMarkdownPath(doc.getKnowledgeId(), doc.getFilePath());
                minioUtil.uploadString(markdownContent, markdownPath, "text/markdown");
                doc.setMarkdownPath(markdownPath);
            }

            // 5. 更新文档状态为 UPLOADED
            doc.setStatus(DocumentStatus.UPLOADED);
            documentService.updateById(doc);
            taskService.updateProgress(task.getId(), 95, "处理完成");

            // 6. 清理临时文件
            try {
                Files.deleteIfExists(temp);
            } catch (Exception e) {
                log.warn("[文档上传执行器] 临时文件清理失败, tempPath={}", tempPath, e);
            }

            log.info("[文档上传执行器] 完成, documentId={}", documentId);

        } catch (Exception e) {
            log.error("[文档上传执行器] 失败, documentId={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(buildErrorMessage(e));
            documentService.updateById(doc);
            // 清理临时文件
            try {
                Files.deleteIfExists(Path.of(tempPath));
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private boolean isContentTooShort(String content, String fileType) {
        if (content == null || content.isBlank()) {
            return true;
        }
        if ("pdf".equals(fileType) || "jpg".equals(fileType) || "jpeg".equals(fileType) || "png".equals(fileType)) {
            return content.trim().length() < 50;
        }
        return false;
    }

    private String tryOcr(InputStream inputStream, String fileType) {
        try {
            if ("pdf".equals(fileType)) {
                return ocrUtil.recognizePdf(inputStream);
            } else if ("jpg".equals(fileType) || "jpeg".equals(fileType) || "png".equals(fileType)
                    || "bmp".equals(fileType) || "tiff".equals(fileType) || "tif".equals(fileType)) {
                return ocrUtil.recognizeImage(inputStream);
            }
        } catch (Exception e) {
            log.warn("[OCR] 识别失败, fileType={}", fileType, e);
        }
        return null;
    }

    private String mergeOcrContent(String originalContent, String ocrContent) {
        if (originalContent == null || originalContent.isBlank()) {
            return ocrContent;
        }
        return originalContent + "\n\n---\n\n## OCR 识别内容\n\n" + ocrContent;
    }

    private String generateMarkdownPath(Long knowledgeId, String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return String.format("knowledge/%d/parsed/%s.md", knowledgeId, baseName);
    }

    private String buildErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        StackTraceElement[] stack = e.getStackTrace();
        if (stack.length > 0) {
            StringBuilder sb = new StringBuilder(msg);
            for (int i = 0; i < Math.min(3, stack.length); i++) {
                sb.append("\n  at ").append(stack[i].getClassName())
                        .append(".").append(stack[i].getMethodName())
                        .append(":").append(stack[i].getLineNumber());
            }
            return sb.toString();
        }
        return msg;
    }
}
