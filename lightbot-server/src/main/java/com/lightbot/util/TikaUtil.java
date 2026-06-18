package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档解析工具
 * <p>支持 PDF、DOC、DOCX、PPT、PPTX、XLS、XLSX、TXT、MD、HTML、CSV 等格式</p>
 * <p>使用 Spring AI 的 TikaDocumentReader（Tika 3.2.3）统一解析所有格式</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class TikaUtil {

    /** 支持的文件扩展名 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "md", "txt", "pdf", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "csv", "html", "htm"
    );

    /**
     * 判断文件类型是否支持
     *
     * @param extension 文件扩展名（不含点号）
     * @return 是否支持
     */
    public boolean isSupported(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * 解析文件内容为纯文本
     * <p>使用 TikaDocumentReader 统一解析所有格式，解析失败返回 null</p>
     *
     * @param inputStream 文件输入流
     * @param filename    文件名（用于日志）
     * @return 解析后的纯文本内容，解析失败返回 null
     */
    public String parse(InputStream inputStream, String filename) {
        try {
            TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(inputStream));
            String content = reader.get().get(0).getText();

            if (content == null || content.isBlank()) {
                log.warn("[TikaUtil] 文档解析结果为空: filename={}", filename);
                return null;
            }

            log.info("[TikaUtil] 文档解析成功: filename={}, length={}", filename, content.length());
            return content;
        } catch (Exception e) {
            log.warn("[TikaUtil] 文档解析失败: filename={}, error={}", filename, e.getMessage());
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 解析文件内容为 Markdown 格式
     * <p>Excel/CSV 转 Markdown 表格，Word 提取表格结构，其他格式返回纯文本</p>
     *
     * @param inputStream 文件输入流
     * @param filename    文件名（用于日志和格式判断）
     * @return Markdown 格式内容，解析失败返回 null
     */
    public String parseToMarkdown(InputStream inputStream, String filename) {
        String ext = getExtension(filename);
        try {
            return switch (ext) {
                case "xlsx" -> parseExcelToMarkdown(inputStream, false);
                case "xls" -> parseExcelToMarkdown(inputStream, true);
                case "csv" -> parseCsvToMarkdown(inputStream);
                case "docx" -> parseDocxToMarkdown(inputStream);
                case "doc" -> parseDocToMarkdown(inputStream);
                default -> parse(inputStream, filename);
            };
        } catch (Exception e) {
            log.warn("[TikaUtil] Markdown转换失败: filename={}, error={}", filename, e.getMessage());
            return parse(inputStream, filename);
        }
    }

    /**
     * DOCX 转 Markdown（保留表格结构）
     */
    private String parseDocxToMarkdown(InputStream inputStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (var element : doc.getBodyElements()) {
                if (element instanceof XWPFTable table) {
                    appendDocxTableAsMarkdown(sb, table);
                    sb.append("\n");
                } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph para) {
                    String text = para.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append("\n\n");
                    }
                }
            }
            return sb.toString().trim();
        }
    }

    private void appendDocxTableAsMarkdown(StringBuilder sb, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        boolean isFirst = true;
        for (XWPFTableRow row : rows) {
            sb.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                String val = cell.getText().replace("|", "\\|").replace("\n", " ");
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
            if (isFirst) {
                sb.append("|");
                for (int i = 0; i < row.getTableCells().size(); i++) {
                    sb.append("---|");
                }
                sb.append("\n");
                isFirst = false;
            }
        }
    }

    /**
     * DOC 转 Markdown（保留表格结构）
     */
    private String parseDocToMarkdown(InputStream inputStream) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(inputStream)) {
            Range range = doc.getRange();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph para = range.getParagraph(i);
                if (para.isInTable()) {
                    Table table = range.getTable(para);
                    if (table.numParagraphs() > 0 && table.getParagraph(0) == para) {
                        appendDocTableAsMarkdown(sb, table);
                        sb.append("\n");
                    }
                } else {
                    String text = para.text();
                    if (text != null && !text.isBlank()) {
                        sb.append(text.strip()).append("\n\n");
                    }
                }
            }
            return sb.toString().trim();
        }
    }

    private void appendDocTableAsMarkdown(StringBuilder sb, Table table) {
        boolean isFirst = true;
        for (int r = 0; r < table.numRows(); r++) {
            TableRow row = table.getRow(r);
            sb.append("|");
            for (int c = 0; c < row.numCells(); c++) {
                TableCell cell = row.getCell(c);
                String val = cell.text().replace("|", "\\|").replace("\n", " ").strip();
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
            if (isFirst) {
                sb.append("|");
                for (int c = 0; c < row.numCells(); c++) {
                    sb.append("---|");
                }
                sb.append("\n");
                isFirst = false;
            }
        }
    }

    /**
     * Excel 转 Markdown 表格
     */
    private String parseExcelToMarkdown(InputStream inputStream, boolean isHssf) throws IOException {
        try (Workbook workbook = isHssf ? new HSSFWorkbook(inputStream) : new XSSFWorkbook(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }
                if (i > 0) {
                    sb.append("\n\n");
                }
                sb.append("## ").append(sheet.getSheetName()).append("\n\n");
                appendSheetAsMarkdownTable(sb, sheet);
            }
            return sb.toString();
        }
    }

    /**
     * 将 Sheet 转为 Markdown 表格
     */
    private void appendSheetAsMarkdownTable(StringBuilder sb, Sheet sheet) {
        // 1. 计算最大列数
        int maxCol = 0;
        for (Row row : sheet) {
            maxCol = Math.max(maxCol, row.getLastCellNum());
        }
        if (maxCol == 0) {
            return;
        }

        // 2. 遍历所有行
        boolean isFirst = true;
        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            sb.append("|");
            for (int c = 0; c < maxCol; c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                sb.append(" ").append(getCellValue(cell)).append(" |");
            }
            sb.append("\n");

            // 3. 第一行后插入分隔行
            if (isFirst) {
                sb.append("|");
                for (int c = 0; c < maxCol; c++) {
                    sb.append("---|");
                }
                sb.append("\n");
                isFirst = false;
            }
        }
    }

    /**
     * 获取单元格文本值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().replace("|", "\\|").replace("\n", " ");
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) && !Double.isInfinite(val)
                        ? String.valueOf((long) val)
                        : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    /**
     * CSV 转 Markdown 表格
     */
    private String parseCsvToMarkdown(InputStream inputStream) throws IOException {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                sb.append("|");
                for (String cell : cells) {
                    String val = cell.trim().replaceAll("^\"|\"$", "").replace("|", "\\|");
                    sb.append(" ").append(val).append(" |");
                }
                sb.append("\n");
                if (isFirst) {
                    sb.append("|");
                    for (int i = 0; i < cells.length; i++) {
                        sb.append("---|");
                    }
                    sb.append("\n");
                    isFirst = false;
                }
            }
            return sb.toString();
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    // ==================== DOCX 图片提取 ====================

    /** 支持的图片 MIME 类型 */
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/bmp"
    );

    /** 图片大小上限：10MB */
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    /**
     * DOCX 图片数据
     *
     * @param placeholder Markdown 中的占位符（如 {{IMG:uuid}}）
     * @param data        图片二进制数据
     * @param contentType 图片 MIME 类型
     * @param fileName    原始文件名
     */
    public record ImageData(String placeholder, byte[] data, String contentType, String fileName) {}

    /**
     * Markdown 解析结果（含图片）
     *
     * @param markdown Markdown 文本（含图片占位符）
     * @param images   提取的图片列表
     */
    public record MarkdownResult(String markdown, List<ImageData> images) {}

    /**
     * 解析 DOCX 为 Markdown，同时提取内嵌图片
     * <p>图片以 {{IMG:uuid}} 占位符插入 Markdown，调用方负责上传图片并替换占位符为实际 URL</p>
     *
     * @param inputStream DOCX 文件输入流
     * @return MarkdownResult 包含 Markdown 文本和图片列表
     */
    public MarkdownResult parseDocxWithImages(InputStream inputStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            List<ImageData> images = new ArrayList<>();
            // rId 去重：同一 rId 只收集一次
            Map<String, String> rIdToPlaceholder = new HashMap<>();

            for (var element : doc.getBodyElements()) {
                if (element instanceof XWPFTable table) {
                    appendDocxTableAsMarkdown(sb, table);
                    sb.append("\n");
                } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph para) {
                    // 遍历 Run，分别处理文本和图片
                    for (var run : para.getRuns()) {
                        // 文本内容
                        String text = run.text();
                        if (text != null && !text.isBlank()) {
                            sb.append(text);
                        }
                        // 内嵌图片
                        extractRunPictures(run, sb, images, rIdToPlaceholder);
                    }
                    // 段落结束换行
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append("\n\n");
                    }
                }
            }
            return new MarkdownResult(sb.toString().trim(), images);
        }
    }

    /**
     * 提取 Run 中的图片
     */
    private void extractRunPictures(org.apache.poi.xwpf.usermodel.XWPFRun run,
                                    StringBuilder sb, List<ImageData> images, Map<String, String> rIdToPlaceholder) {
        var pictures = run.getEmbeddedPictures();
        if (pictures == null || pictures.isEmpty()) {
            return;
        }
        for (var pic : pictures) {
            String rId = pic.getPictureData() != null ? pic.getPictureData().getPackagePart().getPartName().getName() : null;
            // 同一图片去重
            if (rId != null && rIdToPlaceholder.containsKey(rId)) {
                sb.append(rIdToPlaceholder.get(rId));
                continue;
            }
            // 检查图片格式
            var picData = pic.getPictureData();
            if (picData == null) {
                continue;
            }
            var picType = picData.getPictureTypeEnum();
            String contentType = picType != null ? picType.getContentType() : null;
            if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                sb.append("[图片: 不支持的格式]");
                continue;
            }
            // 检查大小
            byte[] data = picData.getData();
            if (data == null || data.length > MAX_IMAGE_SIZE) {
                sb.append("[图片: 文件过大]");
                continue;
            }
            // 生成占位符
            String placeholder = "{{IMG:" + UUID.randomUUID() + "}}";
            String ext = contentTypeToExt(contentType);
            String fileName = "image_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            images.add(new ImageData(placeholder, data, contentType, fileName));
            sb.append(placeholder);
            if (rId != null) {
                rIdToPlaceholder.put(rId, placeholder);
            }
        }
    }

    /**
     * MIME 类型转扩展名
     */
    private String contentTypeToExt(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/bmp" -> "bmp";
            default -> "bin";
        };
    }

    /**
     * 解析 DOCX 为 Markdown（含图片），对外公共方法
     * <p>内部将输入流缓冲为字节数组，确保图片提取失败时可降级重试</p>
     *
     * @param inputStream DOCX 文件输入流
     * @return MarkdownResult
     */
    public MarkdownResult parseDocxToMarkdownWithImages(InputStream inputStream) {
        // 缓冲到内存，因为 XWPFDocument 构造器会关闭流，需要两次读取机会
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("[TikaUtil] 读取DOCX流失败: error={}", e.getMessage());
            return new MarkdownResult("", List.of());
        }
        try {
            return parseDocxWithImages(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("[TikaUtil] DOCX图片提取失败, 降级为纯文本解析: error={}", e.getMessage());
            try {
                String fallback = parse(new ByteArrayInputStream(bytes), "fallback.docx");
                return new MarkdownResult(fallback != null ? fallback : "", List.of());
            } catch (Exception ex) {
                return new MarkdownResult("", List.of());
            }
        }
    }
}
