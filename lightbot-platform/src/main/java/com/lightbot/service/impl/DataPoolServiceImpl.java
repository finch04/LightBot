package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.entity.DataModel;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.DataModelService;
import com.lightbot.service.DataPoolService;
import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.util.DataModelSchemaSupport;
import com.lightbot.util.DataPoolImportExcelUtil;
import com.lightbot.util.DataPoolJdbcUtil;
import com.lightbot.util.MinioUtil;
import com.lightbot.vo.DataPoolImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据池服务：批量 CRUD / 导入导出
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPoolServiceImpl implements DataPoolService {

    private static final long MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024L;

    private final DataModelService dataModelService;
    private final DataModelSchemaSupport schemaSupport;
    private final DataPoolJdbcUtil poolJdbcUtil;
    private final DataPoolImportExcelUtil importExcelUtil;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;

    @Override
    public Page<Map<String, Object>> page(Long modelId, int pageNum, int pageSize,
                                          String keyword, Map<String, Object> filters) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        long total = poolJdbcUtil.count(model.getTableName(), schema, keyword, filters);
        List<Map<String, Object>> records = poolJdbcUtil.page(
                model.getTableName(), schema, keyword, filters, pageNum, pageSize);
        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(records);
        return page;
    }

    @Override
    public Map<String, Object> get(Long modelId, Long recordId) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        Map<String, Object> row = poolJdbcUtil.getById(model.getTableName(), schema, recordId);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_POOL_RECORD_NOT_FOUND);
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long modelId, Map<String, Object> data) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        return poolJdbcUtil.insert(model.getTableName(), schema, data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> batchCreate(Long modelId, List<Map<String, Object>> records) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        return poolJdbcUtil.batchInsert(model.getTableName(), schema, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long modelId, Long recordId, Map<String, Object> data) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        return poolJdbcUtil.update(model.getTableName(), schema, recordId, data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long modelId, Long recordId) {
        DataModel model = dataModelService.requireOwned(modelId);
        int n = poolJdbcUtil.softDelete(model.getTableName(), List.of(recordId));
        if (n == 0) {
            throw new BizException(ErrorCode.DATA_POOL_RECORD_NOT_FOUND);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(Long modelId, List<Long> ids) {
        DataModel model = dataModelService.requireOwned(modelId);
        return poolJdbcUtil.softDelete(model.getTableName(), ids);
    }

    @Override
    public DataPoolImportResultVO importData(Long modelId, MultipartFile file, String mode) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "文件为空");
        }
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        List<DataModelSchema.FieldDef> fields = schemaSupport.customFields(schema);
        if (fields.isEmpty()) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "模型尚未配置业务字段");
        }
        List<String> fieldHeaders = fields.stream()
                .map(f -> StringUtils.hasText(f.getLabel()) ? f.getLabel() : f.getKey())
                .toList();
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        boolean csvSource = filename.endsWith(".csv");
        List<ImportLine> lines;
        List<String> sourceHeaders;
        try {
            if (filename.endsWith(".json")) {
                lines = parseJsonLines(file, fields);
                sourceHeaders = fieldHeaders;
            } else if (csvSource) {
                CsvImportBundle bundle = parseCsvBundle(file, fields);
                lines = bundle.lines();
                sourceHeaders = bundle.headers();
            } else {
                throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "仅支持 csv/json");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, e.getMessage());
        }

        // 覆盖模式：先清空再逐行写入（允许部分成功）
        if ("replace".equalsIgnoreCase(mode)) {
            poolJdbcUtil.hardDeleteAll(model.getTableName());
        }

        List<List<String>> sourceRows = new ArrayList<>();
        List<Boolean> successFlags = new ArrayList<>();
        List<String> failReasons = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        for (ImportLine line : lines) {
            sourceRows.add(line.sourceCells);
            if (StringUtils.hasText(line.parseError)) {
                successFlags.add(false);
                failReasons.add(line.parseError);
                failCount++;
                continue;
            }
            try {
                // 逐条同步写入，便于精确记录唯一约束等数据库错误
                poolJdbcUtil.insert(model.getTableName(), schema, line.data);
                successFlags.add(true);
                failReasons.add("");
                successCount++;
            } catch (BizException e) {
                successFlags.add(false);
                failReasons.add(e.getMessage() != null ? e.getMessage() : "业务校验失败");
                failCount++;
            } catch (Exception e) {
                successFlags.add(false);
                failReasons.add(simplifyDbError(e));
                failCount++;
            }
        }

        // CSV：在源文件列后追加结果列，避免重建 xlsx 列宽错乱；JSON：回退生成 xlsx
        byte[] resultFile = csvSource
                ? importExcelUtil.appendResultToCsv(sourceHeaders, sourceRows, successFlags, failReasons)
                : importExcelUtil.buildResultWorkbook(fieldHeaders, sourceRows, successFlags, failReasons);
        DataPoolImportResultVO vo = new DataPoolImportResultVO();
        vo.setTotal(lines.size());
        vo.setSuccessCount(successCount);
        vo.setFailCount(failCount);
        vo.setResultFileName(csvSource
                ? "import-result-" + modelId + ".csv"
                : "import-result-" + modelId + ".xlsx");
        vo.setResultFileBase64(Base64.getEncoder().encodeToString(resultFile));
        log.info("[DataCenter] 同步导入完成 modelId={} total={} success={} fail={}",
                modelId, lines.size(), successCount, failCount);
        return vo;
    }

    @Override
    public Map<String, Object> uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "文件为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, "附件不能超过 20MB");
        }
        long userId = StpUtil.getLoginIdAsLong();
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String safeName = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        String path = String.format("datacenter/%d/%s_%s",
                userId, UUID.randomUUID().toString().replace("-", ""), safeName);
        minioUtil.upload(file, path);
        String url = minioUtil.getPublicUrl(path);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", safeName);
        result.put("url", url);
        result.put("path", path);
        result.put("size", file.getSize());
        return result;
    }

    @Override
    public byte[] exportJson(Long modelId, String keyword, Map<String, Object> filters) {
        List<Map<String, Object>> all = loadAll(modelId, keyword, filters);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(all);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_POOL_IMPORT_FAILED, "导出失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportCsv(Long modelId, String keyword, Map<String, Object> filters) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        List<Map<String, Object>> all = loadAll(modelId, keyword, filters);
        // 表头用中文展示名，便于业务阅读；导入侧同时兼容中文名与字段 key
        List<String> headerLabels = new ArrayList<>();
        List<String> valueKeys = new ArrayList<>();
        Map<String, String> keyTypes = new HashMap<>();
        headerLabels.add("ID");
        valueKeys.add("id");
        keyTypes.put("id", "id");
        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            headerLabels.add(StringUtils.hasText(f.getLabel()) ? f.getLabel() : f.getKey());
            valueKeys.add(f.getKey());
            keyTypes.put(f.getKey(), f.getType() != null ? f.getType().toLowerCase(Locale.ROOT) : "input");
        }
        headerLabels.add("创建时间");
        valueKeys.add("createTime");
        keyTypes.put("createTime", "datetime");
        headerLabels.add("更新时间");
        valueKeys.add("updateTime");
        keyTypes.put("updateTime", "datetime");
        StringBuilder sb = new StringBuilder();
        // BOM：Excel 正确识别 UTF-8 中文表头
        sb.append('\uFEFF');
        sb.append(String.join(",", headerLabels.stream().map(this::csvEscape).toList())).append('\n');
        for (Map<String, Object> row : all) {
            List<String> cells = new ArrayList<>();
            for (String key : valueKeys) {
                String type = keyTypes.getOrDefault(key, "input");
                String text = formatTypedExportCell(key, type, row.get(key));
                // 日期时间强制文本单元格，避免 Excel 打开 CSV 时改成 2026/7/23 20:06
                if (isExcelDateLike(type)) {
                    cells.add(csvForceText(text));
                } else if ("id".equals(type)) {
                    // 雪花 ID 同样强制文本，防止科学计数法
                    cells.add(csvForceText(text));
                } else {
                    cells.add(csvEscape(text));
                }
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> loadAll(Long modelId, String keyword, Map<String, Object> filters) {
        DataModel model = dataModelService.requireOwned(modelId);
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        List<Map<String, Object>> all = new ArrayList<>();
        int pageNum = 1;
        int pageSize = 500;
        while (true) {
            List<Map<String, Object>> page = poolJdbcUtil.page(
                    model.getTableName(), schema, keyword, filters, pageNum, pageSize);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return all;
    }

    private List<ImportLine> parseJsonLines(MultipartFile file, List<DataModelSchema.FieldDef> fields) throws Exception {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        List<Map<String, Object>> rawList;
        if (text.startsWith("[")) {
            rawList = objectMapper.readValue(text, new TypeReference<>() {});
        } else {
            Map<String, Object> one = objectMapper.readValue(text, new TypeReference<>() {});
            rawList = List.of(one);
        }
        Map<String, String> aliasToKey = buildHeaderAliasMap(fields);
        List<ImportLine> lines = new ArrayList<>();
        for (Map<String, Object> raw : rawList) {
            Map<String, Object> data = new LinkedHashMap<>();
            List<String> sourceCells = new ArrayList<>();
            if (raw == null) {
                for (int i = 0; i < fields.size(); i++) {
                    sourceCells.add("");
                }
                lines.add(new ImportLine(data, sourceCells, "空对象"));
                continue;
            }
            for (DataModelSchema.FieldDef f : fields) {
                Object v = raw.containsKey(f.getKey()) ? raw.get(f.getKey()) : raw.get(f.getLabel());
                data.put(f.getKey(), v);
                sourceCells.add(v == null ? "" : String.valueOf(v));
            }
            // 兼容仅用别名 key 的情况
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                String mapped = aliasToKey.get(e.getKey());
                if (mapped != null && !data.containsKey(mapped)) {
                    data.put(mapped, e.getValue());
                }
            }
            lines.add(new ImportLine(data, sourceCells, null));
        }
        return lines;
    }

    /**
     * 解析 CSV：保留源文件全部列（用于结果文件原样回写），同时映射业务字段用于入库
     */
    private CsvImportBundle parseCsvBundle(MultipartFile file, List<DataModelSchema.FieldDef> fields) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return new CsvImportBundle(List.of(), List.of());
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = splitCsvLine(headerLine);
            Map<String, String> aliasToKey = buildHeaderAliasMap(fields);
            List<Integer> fieldColIndex = new ArrayList<>();
            for (DataModelSchema.FieldDef f : fields) {
                int idx = -1;
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i).trim();
                    String key = aliasToKey.get(h);
                    if (f.getKey().equals(key)) {
                        idx = i;
                        break;
                    }
                }
                fieldColIndex.add(idx);
            }
            List<ImportLine> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                // 源行列对齐到表头列数，结果文件按源结构追加状态列
                List<String> sourceCells = new ArrayList<>(headers.size());
                for (int i = 0; i < headers.size(); i++) {
                    sourceCells.add(i < cells.size() && cells.get(i) != null ? cells.get(i) : "");
                }
                Map<String, Object> data = new LinkedHashMap<>();
                for (int fi = 0; fi < fields.size(); fi++) {
                    DataModelSchema.FieldDef f = fields.get(fi);
                    int col = fieldColIndex.get(fi);
                    String cell = sanitizeImportCell(col >= 0 && col < sourceCells.size() ? sourceCells.get(col) : "");
                    data.put(f.getKey(), cell.isEmpty() ? null : cell);
                }
                lines.add(new ImportLine(data, sourceCells, null));
            }
            return new CsvImportBundle(headers, lines);
        }
    }

    private Map<String, String> buildHeaderAliasMap(List<DataModelSchema.FieldDef> fields) {
        Map<String, String> map = new HashMap<>();
        for (DataModelSchema.FieldDef f : fields) {
            map.put(f.getKey(), f.getKey());
            if (StringUtils.hasText(f.getLabel())) {
                map.put(f.getLabel().trim(), f.getKey());
            }
        }
        return map;
    }

    private String simplifyDbError(Exception e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage() != null ? cur.getMessage() : e.getMessage();
        if (msg == null) {
            return "写入失败";
        }
        if (msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("Unique")) {
            return "唯一约束冲突，数据已存在";
        }
        if (msg.length() > 200) {
            return msg.substring(0, 200) + "...";
        }
        return msg;
    }

    /** sourceCells：源文件该行原始单元格（CSV 全列 / JSON 按字段顺序） */
    private record ImportLine(Map<String, Object> data, List<String> sourceCells, String parseError) {
    }

    private record CsvImportBundle(List<String> headers, List<ImportLine> lines) {
    }

    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == ',') {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString());
        return result;
    }

    /**
     * CSV 单元格：多选用顿号拼接；附件等对象数组用 JSON，避免 String.valueOf 变成 [object Object]/map toString
     */
    private String formatExportCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> col) {
            if (col.isEmpty()) {
                return "";
            }
            boolean complex = col.stream().anyMatch(item -> item instanceof Map || item instanceof Collection);
            if (!complex) {
                return col.stream().map(String::valueOf).collect(Collectors.joining("、"));
            }
            try {
                return objectMapper.writeValueAsString(col);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        if (value instanceof Map<?, ?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private String formatTypedExportCell(String key, String type, Object value) {
        String text = formatExportCell(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if ("date".equals(type)) {
            return normalizeExportDate(text);
        }
        if ("datetime".equals(type) || "createTime".equals(key) || "updateTime".equals(key)) {
            return normalizeExportDateTime(text);
        }
        return text;
    }

    private boolean isExcelDateLike(String type) {
        return "date".equals(type) || "datetime".equals(type);
    }

    /** 统一为 yyyy-MM-dd */
    private String normalizeExportDate(String raw) {
        String s = raw.trim().replace('T', ' ').replace('/', '-');
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (Exception e) {
            return s;
        }
    }

    /** 统一为 yyyy-MM-dd HH:mm:ss（Excel 导出目标格式） */
    private String normalizeExportDateTime(String raw) {
        String s = raw.trim().replace('T', ' ').replace('/', '-');
        if (s.length() == 10) {
            s = s + " 00:00:00";
        } else if (s.length() == 16) {
            // yyyy-MM-dd HH:mm → 补秒
            s = s + ":00";
        } else if (s.length() > 19) {
            s = s.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 强制 Excel 按文本打开：前缀 tab + 双引号包裹，避免日期被改成 2026/7/23 20:06
     */
    private String csvForceText(String value) {
        if (value == null) {
            value = "";
        }
        String forced = "\t" + value;
        return "\"" + forced.replace("\"", "\"\"") + "\"";
    }

    /** 导入时去掉导出防 Excel 转换的 tab / ="..." 包装 */
    private String sanitizeImportCell(String cell) {
        if (cell == null) {
            return "";
        }
        String s = cell.trim();
        while (s.startsWith("\t")) {
            s = s.substring(1);
        }
        if (s.startsWith("=\"") && s.endsWith("\"") && s.length() >= 3) {
            s = s.substring(2, s.length() - 1);
        } else if (s.startsWith("=") && s.length() > 1) {
            s = s.substring(1).replace("\"", "");
        }
        return s.trim();
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
