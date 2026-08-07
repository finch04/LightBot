package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据模型物理表 DDL：建表、对比后增量改表/索引
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataModelDdlUtil {

    private final JdbcTemplate jdbcTemplate;
    private final DataModelSchemaSupport schemaSupport;

    /**
     * 按 schema 创建物理表及唯一/普通索引
     */
    public void createTable(String tableName, DataModelSchema schema) {
        schemaSupport.assertSafeTableName(tableName);
        schemaSupport.validateSchema(schema);

        String qTable = schemaSupport.quoteIdent(tableName);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(qTable).append(" (\n");
        ddl.append("  ").append(schemaSupport.quoteIdent("id")).append(" BIGINT NOT NULL,\n");
        for (DataModelSchema.FieldDef field : schemaSupport.customFields(schema)) {
            String col = schemaSupport.toColumnName(field.getKey());
            ddl.append("  ").append(schemaSupport.quoteIdent(col)).append(' ')
                    .append(schemaSupport.sqlTypeOf(field.getType()));
            if (Boolean.TRUE.equals(field.getRequired())) {
                ddl.append(" NOT NULL");
            }
            ddl.append(",\n");
        }
        ddl.append("  ").append(schemaSupport.quoteIdent("create_time"))
                .append(" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
        ddl.append("  ").append(schemaSupport.quoteIdent("update_time"))
                .append(" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
        ddl.append("  ").append(schemaSupport.quoteIdent("deleted"))
                .append(" SMALLINT NOT NULL DEFAULT 0,\n");
        ddl.append("  PRIMARY KEY (").append(schemaSupport.quoteIdent("id")).append(")\n)");

        try {
            jdbcTemplate.execute(ddl.toString());
            applyColumnComments(tableName, schema);
            ensureConstraints(tableName, schema, Collections.emptySet(), Collections.emptyMap());
            log.info("[DataCenter] 创建物理表成功 table={}", tableName);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_MODEL_DDL_FAILED, e.getMessage());
        }
    }

    /**
     * 对比现有表结构/索引后增量同步（新增列、新增/重建索引；不删列以免丢数据）
     */
    public void syncTable(String tableName, DataModelSchema schema) {
        syncTable(tableName, schema, null);
    }

    /**
     * 对比现有表结构/索引后增量同步
     *
     * @param previousSchema 保存前的旧 schema；用于识别「仅改英文名」并执行 RENAME COLUMN
     */
    public void syncTable(String tableName, DataModelSchema schema, DataModelSchema previousSchema) {
        schemaSupport.assertSafeTableName(tableName);
        schemaSupport.validateSchema(schema);
        if (!tableExists(tableName)) {
            createTable(tableName, schema);
            return;
        }

        try {
            Set<String> existingCols = listColumns(tableName);
            // 先尝试一对一改名（如 field_1 → plate_no），避免只加新列留下孤儿列
            applySingleFieldRenames(tableName, previousSchema, schema, existingCols);
            existingCols = listColumns(tableName);

            for (DataModelSchema.FieldDef field : schemaSupport.customFields(schema)) {
                String col = schemaSupport.toColumnName(field.getKey());
                if (!existingCols.contains(col)) {
                    String sql = "ALTER TABLE " + schemaSupport.quoteIdent(tableName)
                            + " ADD COLUMN " + schemaSupport.quoteIdent(col) + " "
                            + schemaSupport.sqlTypeOf(field.getType());
                    jdbcTemplate.execute(sql);
                    log.info("[DataCenter] 新增列 table={} column={}", tableName, col);
                }
            }
            // 新建/已有列都同步备注（「字段名：描述」），供库内注释与大模型分析对齐
            applyColumnComments(tableName, schema);

            Map<String, List<String>> existingIndexes = listManagedIndexes(tableName);
            ensureConstraints(tableName, schema, existingIndexes.keySet(), existingIndexes);
            log.info("[DataCenter] 同步物理表完成 table={}", tableName);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_MODEL_DDL_FAILED, e.getMessage());
        }
    }

    /**
     * 旧 schema 相对新 schema：恰好少 1 个 key、多 1 个 key，且类型相同 → 视为改英文名并 RENAME
     */
    private void applySingleFieldRenames(String tableName,
                                         DataModelSchema previousSchema,
                                         DataModelSchema schema,
                                         Set<String> existingCols) {
        if (previousSchema == null) {
            return;
        }
        Map<String, DataModelSchema.FieldDef> oldMap = schemaSupport.customFields(previousSchema).stream()
                .collect(Collectors.toMap(DataModelSchema.FieldDef::getKey, f -> f, (a, b) -> a, LinkedHashMap::new));
        Map<String, DataModelSchema.FieldDef> newMap = schemaSupport.customFields(schema).stream()
                .collect(Collectors.toMap(DataModelSchema.FieldDef::getKey, f -> f, (a, b) -> a, LinkedHashMap::new));
        Set<String> removed = new LinkedHashSet<>(oldMap.keySet());
        removed.removeAll(newMap.keySet());
        Set<String> added = new LinkedHashSet<>(newMap.keySet());
        added.removeAll(oldMap.keySet());
        if (removed.size() != 1 || added.size() != 1) {
            return;
        }
        String oldKey = removed.iterator().next();
        String newKey = added.iterator().next();
        DataModelSchema.FieldDef oldField = oldMap.get(oldKey);
        DataModelSchema.FieldDef newField = newMap.get(newKey);
        if (oldField == null || newField == null) {
            return;
        }
        String oldType = oldField.getType() != null ? oldField.getType() : "";
        String newType = newField.getType() != null ? newField.getType() : "";
        if (!oldType.equalsIgnoreCase(newType)) {
            return;
        }
        String oldCol = schemaSupport.toColumnName(oldKey);
        String newCol = schemaSupport.toColumnName(newKey);
        if (!existingCols.contains(oldCol) || existingCols.contains(newCol) || oldCol.equals(newCol)) {
            return;
        }
        String sql = "ALTER TABLE " + schemaSupport.quoteIdent(tableName)
                + " RENAME COLUMN " + schemaSupport.quoteIdent(oldCol)
                + " TO " + schemaSupport.quoteIdent(newCol);
        jdbcTemplate.execute(sql);
        existingCols.remove(oldCol);
        existingCols.add(newCol);
        log.info("[DataCenter] 重命名列 table={} {} → {}", tableName, oldCol, newCol);
    }

    public void dropTableIfExists(String tableName) {
        schemaSupport.assertSafeTableName(tableName);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + schemaSupport.quoteIdent(tableName) + " CASCADE");
    }

    /**
     * 同步物理表备注：有描述时为「名称：描述」，否则仅为名称
     *
     * @param tableName   物理表名
     * @param name        数据模型名称
     * @param description 数据模型描述（可空）
     */
    public void applyTableComment(String tableName, String name, String description) {
        schemaSupport.assertSafeTableName(tableName);
        if (!tableExists(tableName)) {
            return;
        }
        String comment = buildTableComment(name, description);
        String sql = "COMMENT ON TABLE " + schemaSupport.quoteIdent(tableName)
                + " IS " + quoteLiteral(comment);
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataCenter] 更新表备注 table={} comment={}", tableName, comment);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_MODEL_DDL_FAILED, e.getMessage());
        }
    }

    /**
     * @return 表备注文本，格式「名称：描述」或仅「名称」
     */
    static String buildTableComment(String name, String description) {
        String modelName = name != null ? name.trim() : "";
        if (!StringUtils.hasText(modelName)) {
            modelName = "未命名";
        }
        String desc = description != null ? description.trim() : "";
        if (!StringUtils.hasText(desc)) {
            return modelName;
        }
        return modelName + "：" + desc;
    }

    public boolean tableExists(String tableName) {
        schemaSupport.assertSafeTableName(tableName);
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)",
                Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    private Set<String> listColumns(String tableName) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, tableName);
        return cols.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 仅管理本系统创建的 uk_/idx_ 前缀索引
     *
     * @return indexName -> ordered column names
     */
    private Map<String, List<String>> listManagedIndexes(String tableName) {
        String sql = """
                SELECT i.relname AS index_name,
                       array_agg(a.attname ORDER BY x.n) AS cols
                FROM pg_class t
                JOIN pg_index ix ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS x(attnum, n) ON TRUE
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = x.attnum
                WHERE t.relkind = 'r'
                  AND t.relname = ?
                  AND (i.relname LIKE 'uk\\_%' OR i.relname LIKE 'idx\\_%')
                GROUP BY i.relname
                """;
        Map<String, List<String>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String name = rs.getString("index_name");
            Object arr = rs.getArray("cols").getArray();
            List<String> cols = new ArrayList<>();
            if (arr instanceof String[] sArr) {
                for (String c : sArr) {
                    cols.add(c.toLowerCase(Locale.ROOT));
                }
            } else if (arr instanceof Object[] oArr) {
                for (Object c : oArr) {
                    cols.add(String.valueOf(c).toLowerCase(Locale.ROOT));
                }
            }
            result.put(name, cols);
        }, tableName);
        return result;
    }

    private void ensureConstraints(String tableName,
                                   DataModelSchema schema,
                                   Set<String> existingIndexNames,
                                   Map<String, List<String>> existingIndexes) {
        Map<String, DesiredIndex> desired = new LinkedHashMap<>();
        for (DataModelSchema.ConstraintRule rule : Optional.ofNullable(schema.getUniqueKeys()).orElseGet(List::of)) {
            String name = schemaSupport.indexName(tableName, "uk", rule.getId());
            desired.put(name, new DesiredIndex(name, true, toColumns(rule.getFields())));
        }
        for (DataModelSchema.ConstraintRule rule : Optional.ofNullable(schema.getIndexes()).orElseGet(List::of)) {
            String name = schemaSupport.indexName(tableName, "idx", rule.getId());
            desired.put(name, new DesiredIndex(name, false, toColumns(rule.getFields())));
        }

        // 删除不再需要或字段组合变化的托管索引
        for (String existingName : new HashSet<>(existingIndexNames)) {
            DesiredIndex want = desired.get(existingName);
            List<String> currentCols = existingIndexes.getOrDefault(existingName, List.of());
            if (want == null || !want.columns.equals(currentCols)) {
                jdbcTemplate.execute("DROP INDEX IF EXISTS " + schemaSupport.quoteIdent(existingName));
                log.info("[DataCenter] 删除索引 table={} index={}", tableName, existingName);
            }
        }

        // 创建缺失索引
        Set<String> afterDrop = listManagedIndexes(tableName).keySet();
        for (DesiredIndex want : desired.values()) {
            if (afterDrop.contains(want.name)) {
                continue;
            }
            String cols = want.columns.stream()
                    .map(schemaSupport::quoteIdent)
                    .collect(Collectors.joining(", "));
            String sql = (want.unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    + schemaSupport.quoteIdent(want.name)
                    + " ON " + schemaSupport.quoteIdent(tableName)
                    + " (" + cols + ") WHERE " + schemaSupport.quoteIdent("deleted") + " = 0";
            jdbcTemplate.execute(sql);
            log.info("[DataCenter] 创建索引 table={} index={} unique={}", tableName, want.name, want.unique);
        }
    }

    private List<String> toColumns(List<String> fieldKeys) {
        List<String> cols = new ArrayList<>();
        for (String key : fieldKeys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            cols.add(schemaSupport.toColumnName(key));
        }
        if (cols.isEmpty()) {
            throw new BizException(ErrorCode.DATA_MODEL_SCHEMA_INVALID, "索引字段为空");
        }
        return cols;
    }

    /**
     * 为自定义列写入 COMMENT：有描述时为「字段名：描述」，否则仅为字段名
     */
    private void applyColumnComments(String tableName, DataModelSchema schema) {
        String qTable = schemaSupport.quoteIdent(tableName);
        for (DataModelSchema.FieldDef field : schemaSupport.customFields(schema)) {
            String col = schemaSupport.toColumnName(field.getKey());
            String comment = buildColumnComment(field);
            String sql = "COMMENT ON COLUMN " + qTable + "." + schemaSupport.quoteIdent(col)
                    + " IS " + quoteLiteral(comment);
            jdbcTemplate.execute(sql);
        }
    }

    /**
     * @return DDL 列备注文本，格式「字段名：xxx」或仅「字段名」
     */
    static String buildColumnComment(DataModelSchema.FieldDef field) {
        String label = field.getLabel() != null ? field.getLabel().trim() : "";
        if (!StringUtils.hasText(label)) {
            label = field.getKey() != null ? field.getKey() : "";
        }
        String description = field.getDescription() != null ? field.getDescription().trim() : "";
        if (!StringUtils.hasText(description)) {
            return label;
        }
        return label + "：" + description;
    }

    private static String quoteLiteral(String value) {
        String safe = value == null ? "" : value.replace("'", "''");
        return "'" + safe + "'";
    }

    private record DesiredIndex(String name, boolean unique, List<String> columns) {
    }
}
