package com.lightbot.util;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.lightbot.config.OcrProperties;
import com.lightbot.dto.OcrHealthResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR 工具类，封装 RapidOCR 操作
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Component
public class OcrUtil {

    private static final Model MODEL = Model.ONNX_PPOCR_V4;

    private final OcrProperties properties;
    private volatile InferenceEngine engine;
    private volatile boolean initialized = false;

    public OcrUtil(OcrProperties properties) {
        this.properties = properties;
    }

    /**
     * 健康检查：尝试初始化引擎验证可用性，回显完整模型路径
     */
    public OcrHealthResult healthCheck() {
        String configPath = resolveModelPath();

        try {
            ensureInitialized();

            // 模型信息：配置路径 + 实际加载的模型版本
            String modelInfo = String.format("配置路径: %s\n模型版本: %s\n模型目录: %s\n检测: %s\n识别: %s\n字典: %s",
                    configPath,
                    MODEL.name(),
                    MODEL.getModelsDir(),
                    MODEL.getDetName(),
                    MODEL.getRecName(),
                    MODEL.getKeysName());

            return OcrHealthResult.builder()
                    .healthy(true)
                    .modelPath(modelInfo)
                    .modelExists(true)
                    .message("OCR服务正常")
                    .build();
        } catch (Exception e) {
            log.error("[OCR] 引擎初始化失败", e);
            return OcrHealthResult.builder()
                    .healthy(false)
                    .modelPath(configPath)
                    .modelExists(false)
                    .message("引擎初始化失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 图片 OCR 识别
     *
     * @param imageStream 图片输入流
     * @return 识别结果文本
     */
    public String recognizeImage(InputStream imageStream) throws Exception {
        ensureInitialized();

        Path tempFile = Files.createTempFile("ocr_", ".png");
        try {
            BufferedImage image = ImageIO.read(imageStream);
            ImageIO.write(image, "png", tempFile.toFile());

            OcrResult result = engine.runOcr(tempFile.toString());
            String text = result != null ? result.getStrRes().trim() : "";
            log.info("[OCR] 图片识别完成, 文本长度={}", text.length());
            return text;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * PDF OCR 识别（逐页转图片再识别）
     *
     * @param pdfStream PDF 输入流
     * @return 识别结果文本
     */
    public String recognizePdf(InputStream pdfStream) throws Exception {
        ensureInitialized();

        List<String> pageResults = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.info("[OCR] PDF共{}页, 开始逐页识别", totalPages);

            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 200, ImageType.RGB);

                Path tempFile = Files.createTempFile("ocr_pdf_", ".png");
                try {
                    ImageIO.write(image, "png", tempFile.toFile());
                    OcrResult result = engine.runOcr(tempFile.toString());
                    String pageText = result != null ? result.getStrRes().trim() : "";
                    if (!pageText.isBlank()) {
                        pageResults.add(pageText);
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }

                log.debug("[OCR] PDF第{}页识别完成", i + 1);
            }
        }

        String result = String.join("\n\n", pageResults);
        log.info("[OCR] PDF识别完成, 共{}页, 文本长度={}", pageResults.size(), result.length());
        return result;
    }

    /**
     * 确保 OCR 引擎已初始化
     */
    private synchronized void ensureInitialized() {
        if (!initialized) {
            engine = InferenceEngine.getInstance(MODEL);
            initialized = true;
            log.info("[OCR] RapidOCR引擎初始化完成, model={}, modelsDir={}", MODEL.name(), MODEL.getModelsDir());
        }
    }

    /**
     * 解析配置中的模型路径（用于健康检查展示）
     */
    private String resolveModelPath() {
        String modelPath = properties.getModelPath();
        if (modelPath == null || modelPath.isBlank()) {
            return "models (默认)";
        }
        return modelPath;
    }
}
