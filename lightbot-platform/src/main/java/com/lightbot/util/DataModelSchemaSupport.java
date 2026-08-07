package com.lightbot.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.enums.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据模型 schema / 标识符校验与转换
 *
 * @author finch
 * @since 2026-07-26
 */
@Component
public class DataModelSchemaSupport {

    public static final String TABLE_PREFIX = "sjc_data_";
    private static final Pattern TABLE_SUFFIX = Pattern.compile("^[a-z][a-z0-9_]{0,47}$");
    private static final Pattern FIELD_KEY = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Pattern RULE_ID = Pattern.compile("^[a-zA-Z0-9_]{1,48}$");
    private static final Set<String> SYSTEM_FIELD_KEYS = Set.of("id", "createTime", "updateTime", "deleted");
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "create_time", "update_time", "deleted");

    private final ObjectMapper objectMapper;

    public DataModelSchemaSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param suffix 不含前缀的表名后缀
     * @return 完整物理表名
     */
    public String buildTableName(String suffix) {
        if (!StringUtils.hasText(suffix) || !TABLE_SUFFIX.matcher(suffix).matches()) {
            throw new BizException(ErrorCode.DATA_MODEL_TABLE_INVALID, suffix);
        }
        return TABLE_PREFIX + suffix;
    }

    public void assertSafeTableName(String tableName) {
        if (!StringUtils.hasText(tableName)
                || !tableName.startsWith(TABLE_PREFIX)
                || !TABLE_SUFFIX.matcher(tableName.substring(TABLE_PREFIX.length())).matches()) {
            throw new BizException(ErrorCode.DATA_MODEL_TABLE_INVALID, tableName);
        }
    }

    public String quoteIdent(String ident) {
        if (!StringUtils.hasText(ident) || !ident.matches("^[a-z][a-z0-9_]{0,62}$")) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "非法标识符: " + ident);
        }
        return "\"" + ident + "\"";
    }

    /**
     * 字段 key（前端 camelCase）→ 物理列名（snake_case）
     */
    public String toColumnName(String fieldKey) {
        if (!StringUtils.hasText(fieldKey) || !FIELD_KEY.matcher(fieldKey).matches()) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "非法字段标识: " + fieldKey);
        }
        if ("createTime".equals(fieldKey)) {
            return "create_time";
        }
        if ("updateTime".equals(fieldKey)) {
            return "update_time";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldKey.length(); i++) {
            char c = fieldKey.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        String col = sb.toString().toLowerCase(Locale.ROOT);
        if (!col.matches("^[a-z][a-z0-9_]{0,62}$")) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "非法列名: " + col);
        }
        return col;
    }

    public boolean isSystemFieldKey(String key) {
        return SYSTEM_FIELD_KEYS.contains(key);
    }

    public boolean isSystemColumn(String column) {
        return SYSTEM_COLUMNS.contains(column);
    }

    public List<DataModelSchema.FieldDef> customFields(DataModelSchema schema) {
        if (schema == null || schema.getFields() == null) {
            return List.of();
        }
        return schema.getFields().stream()
                .filter(f -> f != null
                        && StringUtils.hasText(f.getKey())
                        && !Boolean.TRUE.equals(f.getSystem())
                        && !isSystemFieldKey(f.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * 移除客户端不应写入的系统字段（id / 创建时间 / 更新时间等）
     */
    public Map<String, Object> stripSystemKeys(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data == null ? Map.of() : data;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>(data);
        cleaned.keySet().removeIf(this::isSystemFieldKey);
        cleaned.remove("create_time");
        cleaned.remove("update_time");
        return cleaned;
    }

    public void validateSchema(DataModelSchema schema) {
        if (schema == null) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "schema 为空");
        }
        Set<String> keys = new HashSet<>();
        for (DataModelSchema.FieldDef field : Optional.ofNullable(schema.getFields()).orElseGet(List::of)) {
            if (field == null || !StringUtils.hasText(field.getKey())) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "字段 key 不能为空");
            }
            if (!FIELD_KEY.matcher(field.getKey()).matches()) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "非法字段标识: " + field.getKey());
            }
            if (!keys.add(field.getKey())) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "字段重复: " + field.getKey());
            }
            if (!StringUtils.hasText(field.getType())) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "字段类型不能为空: " + field.getKey());
            }
            if (field.getDescription() != null && field.getDescription().length() > 100) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID,
                        "字段描述不能超过100字: " + field.getKey());
            }
            // 校验可生成列名
            if (!isSystemFieldKey(field.getKey())) {
                toColumnName(field.getKey());
            }
        }
        Set<String> allKeys = new HashSet<>(keys);
        allKeys.addAll(SYSTEM_FIELD_KEYS);
        validateFieldRefs("fuzzySearchFields", schema.getFuzzySearchFields(), allKeys);
        validateFieldRefs("searchConditions", schema.getSearchConditions(), allKeys);
        validateRules("uniqueKeys", schema.getUniqueKeys(), allKeys);
        validateRules("indexes", schema.getIndexes(), allKeys);
    }

    private void validateFieldRefs(String name, List<String> refs, Set<String> allKeys) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            if (!allKeys.contains(ref)) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 引用了不存在的字段: " + ref);
            }
        }
    }

    private void validateRules(String name, List<DataModelSchema.ConstraintRule> rules, Set<String> allKeys) {
        if (rules == null) {
            return;
        }
        Set<String> ruleIds = new HashSet<>();
        for (DataModelSchema.ConstraintRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getId()) || !RULE_ID.matcher(rule.getId()).matches()) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 规则 id 不合法");
            }
            if (!ruleIds.add(rule.getId())) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 规则 id 重复: " + rule.getId());
            }
            if (rule.getFields() == null || rule.getFields().isEmpty()) {
                throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 字段列表不能为空: " + rule.getId());
            }
            for (String f : rule.getFields()) {
                if (!allKeys.contains(f)) {
                    throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 引用了不存在的字段: " + f);
                }
                if ("id".equals(f) || "deleted".equals(f)) {
                    throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, name + " 不支持字段: " + f);
                }
            }
        }
    }

    public String sqlTypeOf(String fieldType) {
        if (fieldType == null) {
            return "TEXT";
        }
        return switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "number" -> "NUMERIC";
            case "date" -> "DATE";
            case "datetime" -> "TIMESTAMP";
            case "checkbox", "upload" -> "JSONB";
            case "select", "radio" -> "VARCHAR(128)";
            case "textarea" -> "TEXT";
            default -> "VARCHAR(512)";
        };
    }

    public String indexName(String tableName, String prefix, String ruleId) {
        assertSafeTableName(tableName);
        if (!RULE_ID.matcher(ruleId).matches()) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "非法索引 id: " + ruleId);
        }
        String raw = prefix + "_" + tableName + "_" + ruleId.toLowerCase(Locale.ROOT);
        if (raw.length() <= 63) {
            return raw;
        }
        return raw.substring(0, 63);
    }

    public DataModelSchema parseSchema(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return emptySchema();
        }
        try {
            DataModelSchema schema = objectMapper.readValue(schemaJson, DataModelSchema.class);
            return schema != null ? schema : emptySchema();
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, e.getMessage());
        }
    }

    public String toSchemaJson(DataModelSchema schema) {
        try {
            return objectMapper.writeValueAsString(schema != null ? schema : emptySchema());
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, e.getMessage());
        }
    }

    public DataModelSchema emptySchema() {
        DataModelSchema schema = new DataModelSchema();
        schema.setFields(new ArrayList<>());
        schema.setFuzzySearchFields(new ArrayList<>());
        schema.setSearchConditions(new ArrayList<>());
        schema.setUniqueKeys(new ArrayList<>());
        schema.setIndexes(new ArrayList<>());
        return schema;
    }
}
