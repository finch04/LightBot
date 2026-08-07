package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据池导入结果文件生成：优先在源文件结构上追加结果列，避免重建导致列宽错乱
 *
 * @author finch
 * @since 2026-07-26
 */
@Component
public class DataPoolImportExcelUtil {

    private static final String COL_STATUS = "成功/失败";
    private static final String COL_REASON = "失败原因";

    /**
     * 在源 CSV 表头与各行末尾追加「成功/失败」「失败原因」，保持原列顺序与单元格内容不变
     *
     * @param sourceHeaders 源文件表头
     * @param sourceRows    源文件数据行（与表头列对齐）
     * @param successFlags  是否成功
     * @param failReasons   失败原因
     * @return UTF-8 BOM CSV 字节
     */
    public byte[] appendResultToCsv(List<String> sourceHeaders,
                                    List<List<String>> sourceRows,
                                    List<Boolean> successFlags,
                                    List<String> failReasons) {
        List<String> headers = new ArrayList<>(sourceHeaders != null ? sourceHeaders : List.of());
        headers.add(COL_STATUS);
        headers.add(COL_REASON);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(joinCsv(headers)).append('\n');
        int rowCount = sourceRows != null ? sourceRows.size() : 0;
        for (int i = 0; i < rowCount; i++) {
            List<String> cells = new ArrayList<>(padRow(sourceRows.get(i), sourceHeaders != null ? sourceHeaders.size() : 0));
            boolean ok = successFlags != null && i < successFlags.size() && Boolean.TRUE.equals(successFlags.get(i));
            cells.add(ok ? "成功" : "失败");
            String reason = (failReasons != null && i < failReasons.size()) ? failReasons.get(i) : "";
            cells.add(ok || !StringUtils.hasText(reason) ? "" : reason);
            sb.append(joinCsv(cells)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JSON 等无源表格结构时的回退：生成 xlsx，并按中英文显示宽度设置列宽（不用 autoSizeColumn）
     */
    public byte[] buildResultWorkbook(List<String> headers,
                                      List<List<String>> rowValues,
                                      List<Boolean> successFlags,
                                      List<String> failReasons) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("导入结果");
            List<String> allHeaders = new ArrayList<>(headers != null ? headers : List.of());
            allHeaders.add(COL_STATUS);
            allHeaders.add(COL_REASON);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < allHeaders.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(allHeaders.get(i) != null ? allHeaders.get(i) : "");
            }

            List<List<String>> allRows = new ArrayList<>();
            int n = rowValues != null ? rowValues.size() : 0;
            for (int i = 0; i < n; i++) {
                List<String> values = padRow(rowValues.get(i), headers != null ? headers.size() : 0);
                List<String> full = new ArrayList<>(values);
                boolean ok = successFlags != null && i < successFlags.size() && Boolean.TRUE.equals(successFlags.get(i));
                full.add(ok ? "成功" : "失败");
                String reason = (failReasons != null && i < failReasons.size()) ? failReasons.get(i) : "";
                full.add(ok || !StringUtils.hasText(reason) ? "" : reason);
                allRows.add(full);

                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < full.size(); c++) {
                    row.createCell(c).setCellValue(full.get(c) != null ? full.get(c) : "");
                }
            }
            applyDisplayWidths(sheet, allHeaders, allRows);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "生成结果 Excel 失败: " + e.getMessage());
        }
    }

    private List<String> padRow(List<String> row, int size) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (row != null && i < row.size() && row.get(i) != null) {
                out.add(row.get(i));
            } else {
                out.add("");
            }
        }
        return out;
    }

    private String joinCsv(List<String> cells) {
        List<String> escaped = new ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(csvEscape(cell));
        }
        return String.join(",", escaped);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
                || value.contains("\t");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * 按表头/内容显示宽度设列宽；中文按 2 个半角估算，避免 autoSizeColumn 对中文过窄
     */
    private void applyDisplayWidths(Sheet sheet, List<String> headers, List<List<String>> rows) {
        for (int col = 0; col < headers.size(); col++) {
            int max = displayUnits(headers.get(col));
            for (List<String> row : rows) {
                if (col < row.size()) {
                    max = Math.max(max, displayUnits(row.get(col)));
                }
            }
            // POI 宽度单位：1/256 字符宽；限制上下界防止极端值
            int units = Math.min(Math.max(max + 4, 10), 48);
            sheet.setColumnWidth(col, units * 256);
        }
    }

    private int displayUnits(String text) {
        if (!StringUtils.hasText(text)) {
            return 4;
        }
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c > 0x7F ? 2 : 1;
            if (w >= 40) {
                return 40;
            }
        }
        return w;
    }
}
