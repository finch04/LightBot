package com.lightbot.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.enums.ErrorCode;
import com.lightbot.vo.DataPoolRecordMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 数据池动态表批量读写（JdbcTemplate + 标识符白名单）
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPoolJdbcUtil {

    public static final int BATCH_SIZE = 500;
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final DataModelSchemaSupport schemaSupport;
    private final DataPoolFieldValidator fieldValidator;
    private final ObjectMapper objectMapper;

    public long count(String tableName, DataModelSchema schema, String keyword, Map<String, Object> filters) {
        schemaSupport.assertSafeTableName(tableName);
        SqlParts parts = buildWhere(tableName, schema, keyword, filters);
        String sql = "SELECT COUNT(1) FROM " + schemaSupport.quoteIdent(tableName) + parts.where;
        Long cnt = jdbcTemplate.queryForObject(sql, Long.class, parts.args.toArray());
        return cnt != null ? cnt : 0L;
    }

    public List<Map<String, Object>> page(String tableName, DataModelSchema schema,
                                          String keyword, Map<String, Object> filters,
                                          int pageNum, int pageSize) {
        schemaSupport.assertSafeTableName(tableName);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int offset = (safePage - 1) * safeSize;
        SqlParts parts = buildWhere(tableName, schema, keyword, filters);
        String sql = "SELECT * FROM " + schemaSupport.quoteIdent(tableName)
                + parts.where
                + " ORDER BY " + schemaSupport.quoteIdent("create_time") + " DESC, "
                + schemaSupport.quoteIdent("id") + " DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(parts.args);
        args.add(safeSize);
        args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        return rows.stream().map(r -> toApiRow(r, schema)).toList();
    }

    public Map<String, Object> getById(String tableName, DataModelSchema schema, Long id) {
        schemaSupport.assertSafeTableName(tableName);
        String sql = "SELECT * FROM " + schemaSupport.quoteIdent(tableName)
                + " WHERE " + schemaSupport.quoteIdent("id") + " = ? AND "
                + schemaSupport.quoteIdent("deleted") + " = 0";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            return null;
        }
        return toApiRow(rows.get(0), schema);
    }

    public Map<String, Object> insert(String tableName, DataModelSchema schema, Map<String, Object> data) {
        fieldValidator.validateRecord(schemaSupport.customFields(schema), data, true);
        List<Map<String, Object>> created = batchInsert(tableName, schema, List.of(data));
        return created.get(0);
    }

    /**
     * 批量插入，按 BATCH_SIZE 分批
     *
     * @return 含生成 id 的记录列表（API 字段形态）
     */
    public List<Map<String, Object>> batchInsert(String tableName, DataModelSchema schema,
                                                 List<Map<String, Object>> records) {
        schemaSupport.assertSafeTableName(tableName);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<DataModelSchema.FieldDef> fields = schemaSupport.customFields(schema);
        for (Map<String, Object> raw : records) {
            fieldValidator.validateRecord(fields, schemaSupport.stripSystemKeys(raw), true);
        }
        List<String> columns = new ArrayList<>();
        columns.add("id");
        for (DataModelSchema.FieldDef f : fields) {
            columns.add(schemaSupport.toColumnName(f.getKey()));
        }
        columns.add("create_time");
        columns.add("update_time");
        columns.add("deleted");

        String colSql = columns.stream().map(schemaSupport::quoteIdent).reduce((a, b) -> a + ", " + b).orElse("");
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + schemaSupport.quoteIdent(tableName) + " (" + colSql + ") VALUES (" + placeholders + ")";

        List<Map<String, Object>> result = new ArrayList<>(records.size());
        LocalDateTime now = LocalDateTime.now();
        for (int from = 0; from < records.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, records.size());
            List<Map<String, Object>> slice = records.subList(from, to);
            List<Object[]> batchArgs = new ArrayList<>(slice.size());
            for (Map<String, Object> raw : slice) {
                // 忽略客户端传入的 id/创建时间/更新时间，统一由服务端生成
                Map<String, Object> data = schemaSupport.stripSystemKeys(raw);
                long id = IdWorker.getId();
                Object[] args = new Object[columns.size()];
                int i = 0;
                args[i++] = id;
                Map<String, Object> apiRow = new DataPoolRecordMap();
                apiRow.put("id", String.valueOf(id));
                for (DataModelSchema.FieldDef f : fields) {
                    Object val = normalizeValue(f, data.get(f.getKey()), true);
                    args[i++] = val;
                    apiRow.put(f.getKey(), data.get(f.getKey()));
                }
                args[i++] = Timestamp.valueOf(now);
                args[i++] = Timestamp.valueOf(now);
                args[i] = 0;
                apiRow.put("createTime", DATE_TIME_FMT.format(now));
                apiRow.put("updateTime", DATE_TIME_FMT.format(now));
                batchArgs.add(args);
                result.add(apiRow);
            }
            try {
                jdbcTemplate.batchUpdate(sql, batchArgs);
            } catch (DataIntegrityViolationException e) {
                throw translateUniqueConflict(e, fields);
            }
        }
        log.info("[DataCenter] 批量插入 table={} count={}", tableName, records.size());
        return result;
    }

    public Map<String, Object> update(String tableName, DataModelSchema schema, Long id, Map<String, Object> data) {
        schemaSupport.assertSafeTableName(tableName);
        Map<String, Object> existing = getById(tableName, schema, id);
        if (existing == null) {
            throw new BizException(ErrorCode.DATA_POOL_RECORD_NOT_FOUND);
        }
        List<DataModelSchema.FieldDef> fields = schemaSupport.customFields(schema);
        if (fields.isEmpty()) {
            return existing;
        }
        Map<String, Object> payload = schemaSupport.stripSystemKeys(data);
        // 更新：仅校验本次提交的字段；必填在更新场景不强制补全未传字段
        for (DataModelSchema.FieldDef f : fields) {
            if (payload.containsKey(f.getKey())) {
                fieldValidator.validateField(f, payload.get(f.getKey()), false);
            }
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(schemaSupport.quoteIdent(tableName)).append(" SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (DataModelSchema.FieldDef f : fields) {
            if (!payload.containsKey(f.getKey())) {
                continue;
            }
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(schemaSupport.quoteIdent(schemaSupport.toColumnName(f.getKey()))).append(" = ?");
            args.add(normalizeValue(f, payload.get(f.getKey()), false));
            existing.put(f.getKey(), payload.get(f.getKey()));
        }
        if (args.isEmpty()) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        sql.append(", ").append(schemaSupport.quoteIdent("update_time")).append(" = ?");
        args.add(Timestamp.valueOf(now));
        sql.append(" WHERE ").append(schemaSupport.quoteIdent("id")).append(" = ? AND ")
                .append(schemaSupport.quoteIdent("deleted")).append(" = 0");
        args.add(id);
        int updated;
        try {
            updated = jdbcTemplate.update(sql.toString(), args.toArray());
        } catch (DataIntegrityViolationException e) {
            throw translateUniqueConflict(e, fields);
        }
        if (updated == 0) {
            throw new BizException(ErrorCode.DATA_POOL_RECORD_NOT_FOUND);
        }
        existing.put("updateTime", DATE_TIME_FMT.format(now));
        return existing;
    }

    public int softDelete(String tableName, List<Long> ids) {
        schemaSupport.assertSafeTableName(tableName);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, ids.size());
            List<Long> slice = ids.subList(from, to);
            String placeholders = String.join(", ", Collections.nCopies(slice.size(), "?"));
            String sql = "UPDATE " + schemaSupport.quoteIdent(tableName)
                    + " SET " + schemaSupport.quoteIdent("deleted") + " = 1, "
                    + schemaSupport.quoteIdent("update_time") + " = ?"
                    + " WHERE " + schemaSupport.quoteIdent("deleted") + " = 0 AND "
                    + schemaSupport.quoteIdent("id") + " IN (" + placeholders + ")";
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(now));
            args.addAll(slice);
            total += jdbcTemplate.update(sql, args.toArray());
        }
        return total;
    }

    public int hardDeleteAll(String tableName) {
        schemaSupport.assertSafeTableName(tableName);
        return jdbcTemplate.update("DELETE FROM " + schemaSupport.quoteIdent(tableName));
    }

    private SqlParts buildWhere(String tableName, DataModelSchema schema,
                                String keyword, Map<String, Object> filters) {
        StringBuilder where = new StringBuilder(" WHERE ")
                .append(schemaSupport.quoteIdent("deleted")).append(" = 0");
        List<Object> args = new ArrayList<>();

        Map<String, DataModelSchema.FieldDef> fieldMap = new HashMap<>();
        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            fieldMap.put(f.getKey(), f);
        }

        if (StringUtils.hasText(keyword)) {
            List<String> fuzzy = Optional.ofNullable(schema.getFuzzySearchFields()).orElseGet(List::of);
            List<String> likes = new ArrayList<>();
            for (String key : fuzzy) {
                if (schemaSupport.isSystemFieldKey(key)) {
                    continue;
                }
                if (!fieldMap.containsKey(key)) {
                    continue;
                }
                likes.add(schemaSupport.quoteIdent(schemaSupport.toColumnName(key)) + "::text ILIKE ?");
                args.add("%" + keyword.trim() + "%");
            }
            if (!likes.isEmpty()) {
                where.append(" AND (").append(String.join(" OR ", likes)).append(")");
            }
        }

        if (filters != null) {
            List<String> conditions = Optional.ofNullable(schema.getSearchConditions()).orElseGet(List::of);
            for (String key : conditions) {
                if (!filters.containsKey(key) || filters.get(key) == null || "".equals(filters.get(key))) {
                    continue;
                }
                Object raw = filters.get(key);
                if ("createTime".equals(key)) {
                    where.append(" AND ").append(schemaSupport.quoteIdent("create_time")).append("::date = ?::date");
                    args.add(String.valueOf(raw));
                    continue;
                }
                if ("updateTime".equals(key)) {
                    where.append(" AND ").append(schemaSupport.quoteIdent("update_time")).append("::date = ?::date");
                    args.add(String.valueOf(raw));
                    continue;
                }
                DataModelSchema.FieldDef field = fieldMap.get(key);
                if (field == null) {
                    continue;
                }
                where.append(" AND ").append(schemaSupport.quoteIdent(schemaSupport.toColumnName(key))).append(" = ?");
                args.add(normalizeValue(field, raw, false));
            }
        }
        return new SqlParts(where.toString(), args);
    }

    private Map<String, Object> toApiRow(Map<String, Object> dbRow, DataModelSchema schema) {
        // 使用 DataPoolRecordMap：保留 null 字段，避免全局 Jackson NON_NULL 省略空值
        Map<String, Object> api = new DataPoolRecordMap();
        Object id = dbRow.get("id");
        api.put("id", id != null ? String.valueOf(id) : null);
        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            String col = schemaSupport.toColumnName(f.getKey());
            Object val = dbRow.containsKey(col) ? dbRow.get(col) : dbRow.get(col.toLowerCase(Locale.ROOT));
            api.put(f.getKey(), formatCellValue(f.getType(), val));
        }
        Object createTime = dbRow.get("create_time") != null ? dbRow.get("create_time") : dbRow.get("createTime");
        Object updateTime = dbRow.get("update_time") != null ? dbRow.get("update_time") : dbRow.get("updateTime");
        api.put("createTime", formatDateTime(createTime));
        api.put("updateTime", formatDateTime(updateTime));
        return api;
    }

    /**
     * 统一为页面/导出友好的展示值；日期时间为 yyyy-MM-dd HH:mm:ss。
     * checkbox/upload 的 jsonb 列需反序列化为 List，避免 PGobject 被序列成对象导致前端显示 [object Object]。
     */
    private Object formatCellValue(String fieldType, Object value) {
        if (value == null) {
            return null;
        }
        String type = fieldType != null ? fieldType.toLowerCase(Locale.ROOT) : "";
        if ("date".equals(type)) {
            if (value instanceof Date d) {
                return d.toLocalDate().toString();
            }
            if (value instanceof LocalDate ld) {
                return ld.toString();
            }
            String s = String.valueOf(value);
            return s.length() >= 10 ? s.substring(0, 10) : s;
        }
        if ("datetime".equals(type)) {
            return formatDateTime(value);
        }
        if ("checkbox".equals(type) || "upload".equals(type)) {
            return parseJsonColumn(value);
        }
        return value;
    }

    /**
     * 将 jsonb 列（PGobject / String / 已解析结构）转为 JSON 树（通常为 List）
     */
    private Object parseJsonColumn(Object value) {
        if (value instanceof PGobject pg) {
            String raw = pg.getValue();
            if (!StringUtils.hasText(raw)) {
                return Collections.emptyList();
            }
            try {
                return objectMapper.readValue(raw, Object.class);
            } catch (JsonProcessingException e) {
                log.warn("[DataCenter] jsonb 解析失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        }
        if (value instanceof String s) {
            if (!StringUtils.hasText(s)) {
                return Collections.emptyList();
            }
            try {
                return objectMapper.readValue(s, Object.class);
            } catch (JsonProcessingException e) {
                return Collections.emptyList();
            }
        }
        // 已是 List/Map 等
        return value;
    }

    private String formatDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return DATE_TIME_FMT.format(ts.toLocalDateTime());
        }
        if (value instanceof LocalDateTime ldt) {
            return DATE_TIME_FMT.format(ldt);
        }
        if (value instanceof Date d) {
            return DATE_TIME_FMT.format(d.toLocalDate().atStartOfDay());
        }
        String s = String.valueOf(value).trim().replace('T', ' ');
        if (s.length() >= 19) {
            return s.substring(0, 19);
        }
        return s;
    }

    /**
     * 将唯一约束冲突转为业务异常，尽量带上字段中文名与冲突值
     */
    private BizException translateUniqueConflict(DataIntegrityViolationException e,
                                                 List<DataModelSchema.FieldDef> fields) {
        String detail = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        if (detail == null) {
            detail = "";
        }
        // PG: Key (field_1)=(啊飒飒) already exists.
        String column = null;
        String conflictValue = null;
        int keyIdx = detail.indexOf("Key (");
        if (keyIdx >= 0) {
            int colEnd = detail.indexOf(')', keyIdx);
            if (colEnd > keyIdx) {
                column = detail.substring(keyIdx + 5, colEnd).trim();
            }
            int valStart = detail.indexOf("=(", colEnd);
            if (valStart >= 0) {
                int valEnd = detail.indexOf(')', valStart + 2);
                if (valEnd > valStart) {
                    conflictValue = detail.substring(valStart + 2, valEnd);
                }
            }
        }
        String fieldLabel = column;
        if (StringUtils.hasText(column) && fields != null) {
            for (DataModelSchema.FieldDef f : fields) {
                if (column.equalsIgnoreCase(schemaSupport.toColumnName(f.getKey()))
                        || column.equalsIgnoreCase(f.getKey())) {
                    fieldLabel = StringUtils.hasText(f.getLabel()) ? f.getLabel() : f.getKey();
                    break;
                }
            }
        }
        String msg;
        if (StringUtils.hasText(fieldLabel) && conflictValue != null) {
            msg = fieldLabel + "「" + conflictValue + "」已存在，请修改后重试";
        } else if (StringUtils.hasText(fieldLabel)) {
            msg = fieldLabel + " 与已有数据冲突（唯一约束）";
        } else {
            msg = "与已有数据冲突（唯一约束）";
        }
        return new BizException(ErrorCode.DATA_POOL_UNIQUE_CONFLICT, msg);
    }

    private Object normalizeValue(DataModelSchema.FieldDef field, Object value, boolean forInsert) {
        if (value == null || "".equals(value)) {
            if (forInsert && Boolean.TRUE.equals(field.getRequired())) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, field.getKey() + " 不能为空");
            }
            return null;
        }
        String type = field.getType() != null ? field.getType().toLowerCase(Locale.ROOT) : "input";
        try {
            return switch (type) {
                case "number" -> value instanceof Number n ? n : new BigDecimal(String.valueOf(value));
                case "date" -> {
                    if (value instanceof Date d) {
                        yield d;
                    }
                    if (value instanceof LocalDate ld) {
                        yield Date.valueOf(ld);
                    }
                    yield Date.valueOf(LocalDate.parse(String.valueOf(value).substring(0, 10)));
                }
                case "datetime" -> {
                    if (value instanceof Timestamp ts) {
                        yield ts;
                    }
                    if (value instanceof LocalDateTime ldt) {
                        yield Timestamp.valueOf(ldt);
                    }
                    String s = String.valueOf(value).trim();
                    if (s.length() == 10) {
                        yield Timestamp.valueOf(LocalDate.parse(s).atStartOfDay());
                    }
                    yield Timestamp.valueOf(LocalDateTime.parse(s.replace(' ', 'T')));
                }
                case "checkbox", "upload" -> {
                    try {
                        String json = value instanceof String s ? s : objectMapper.writeValueAsString(value);
                        PGobject pg = new PGobject();
                        pg.setType("jsonb");
                        pg.setValue(json);
                        yield pg;
                    } catch (Exception e) {
                        throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, field.getKey());
                    }
                }
                default -> String.valueOf(value);
            };
        } catch (BizException e) {
            throw e;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, field.getKey() + ": " + e.getMessage());
        }
    }

    private record SqlParts(String where, List<Object> args) {
    }
}
