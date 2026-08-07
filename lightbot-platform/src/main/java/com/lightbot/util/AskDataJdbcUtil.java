package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.dto.askdata.AskDataIntentIR;
import com.lightbot.dto.askdata.AskDimensionDef;
import com.lightbot.dto.askdata.AskFilterDef;
import com.lightbot.dto.askdata.AskMetricDef;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.enums.ErrorCode;
import com.lightbot.vo.DataPoolRecordMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 智能问数只读 JDBC：按 Intent IR 拼参数化 SQL（白名单字段）
 *
 * @author finch
 * @since 2026-07-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AskDataJdbcUtil {

    public static final int MAX_LOOKUP_ROWS = 50;
    public static final int MAX_AGG_GROUPS = 500;
    private static final Set<String> METRIC_OPS = Set.of("count", "count_distinct", "sum", "avg", "min", "max");
    private static final Set<String> FILTER_OPS = Set.of(
            "eq", "ne", "in", "not_in", "like", "not_like", "starts_with", "not_starts_with",
            "gt", "gte", "lt", "lte", "between", "is_null", "is_not_null", "in_last");

    private final JdbcTemplate jdbcTemplate;
    private final DataModelSchemaSupport schemaSupport;

    public record SqlParts(String sql, List<Object> args) {}

    public record QueryRawResult(List<Map<String, Object>> rows, long total, String sql, long elapsedMs) {}

    /**
     * 明细查询
     */
    public QueryRawResult lookup(String tableName, DataModelSchema schema,
                                 List<AskDimensionDef> dimensions,
                                 AskDataIntentIR ir,
                                 List<AskFilterDef> defaultFilters,
                                 List<String> sensitiveFields) {
        long start = System.currentTimeMillis();
        int pageNum = ir.getPageNum() != null && ir.getPageNum() > 0 ? ir.getPageNum() : 1;
        int pageSize = ir.getPageSize() != null && ir.getPageSize() > 0
                ? Math.min(ir.getPageSize(), MAX_LOOKUP_ROWS) : 20;
        if (ir.getLimit() != null && ir.getLimit() > 0) {
            pageSize = Math.min(ir.getLimit(), MAX_LOOKUP_ROWS);
            pageNum = 1;
        }

        SqlParts where = buildWhere(schema, dimensions, ir, defaultFilters);
        String countSql = "SELECT COUNT(1) FROM " + schemaSupport.quoteIdent(tableName) + where.sql();
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, where.args().toArray());

        String order = " ORDER BY " + schemaSupport.quoteIdent("create_time") + " DESC, "
                + schemaSupport.quoteIdent("id") + " DESC";
        String sql = "SELECT * FROM " + schemaSupport.quoteIdent(tableName) + where.sql() + order
                + " LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(where.args());
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);

        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(sql, args.toArray());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> db : dbRows) {
            rows.add(maskSensitive(toApiRow(db, schema), sensitiveFields));
        }
        return new QueryRawResult(rows, total != null ? total : 0, sql, System.currentTimeMillis() - start);
    }

    /**
     * 聚合 / 分组 / 趋势
     */
    public QueryRawResult aggregate(String tableName, DataModelSchema schema,
                                    List<AskDimensionDef> dimensions,
                                    List<AskMetricDef> metrics,
                                    AskDataIntentIR ir,
                                    List<AskFilterDef> defaultFilters,
                                    List<String> sensitiveFields) {
        long start = System.currentTimeMillis();
        List<AskMetricDef> resolvedMetrics = resolveMetrics(metrics, ir);
        if (resolvedMetrics.isEmpty()) {
            AskMetricDef fallback = new AskMetricDef();
            fallback.setCode("cnt");
            fallback.setName("数量");
            fallback.setOp("count");
            resolvedMetrics = List.of(fallback);
        }

        List<String> groupKeys = Optional.ofNullable(ir.getDimensions()).orElseGet(List::of);
        if ("trend".equalsIgnoreCase(ir.getIntent()) && groupKeys.isEmpty()) {
            String timeField = resolveDefaultTimeField(dimensions, ir);
            if (timeField != null) {
                groupKeys = List.of(timeField);
            }
        }
        if (groupKeys.size() > 3) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "groupBy 最多 3 个维度");
        }
        for (String gk : groupKeys) {
            AskDimensionDef dim = findDimension(dimensions, gk);
            if (dim != null && Boolean.TRUE.equals(dim.getHighCardinality())
                    && (ir.getFilters() == null || ir.getFilters().isEmpty())) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "高基数维度 " + gk + " 禁止无过滤分组");
            }
        }

        SqlParts where = buildWhere(schema, dimensions, ir, defaultFilters);
        List<String> selectParts = new ArrayList<>();
        List<String> groupCols = new ArrayList<>();
        for (String gk : groupKeys) {
            String col = resolveColumn(schema, gk);
            String alias = sanitizeAlias(gk);
            String grain = ir.getTimeGrain();
            AskDimensionDef dim = findDimension(dimensions, gk);
            if (dim != null && "time".equalsIgnoreCase(dim.getType()) && StringUtils.hasText(grain)) {
                selectParts.add(timeBucketExpr(col, grain) + " AS " + schemaSupport.quoteIdent(alias));
            } else if ("trend".equalsIgnoreCase(ir.getIntent()) && StringUtils.hasText(ir.getTimeGrain())) {
                selectParts.add(timeBucketExpr(col, ir.getTimeGrain()) + " AS " + schemaSupport.quoteIdent(alias));
            } else {
                selectParts.add(schemaSupport.quoteIdent(col) + " AS " + schemaSupport.quoteIdent(alias));
            }
            groupCols.add(schemaSupport.quoteIdent(alias));
        }
        for (AskMetricDef m : resolvedMetrics) {
            selectParts.add(metricExpr(m, schema) + " AS " + schemaSupport.quoteIdent(sanitizeAlias(m.getCode())));
        }

        int limit = ir.getLimit() != null && ir.getLimit() > 0
                ? Math.min(ir.getLimit(), MAX_AGG_GROUPS) : Math.min(50, MAX_AGG_GROUPS);

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selectParts))
                .append(" FROM ").append(schemaSupport.quoteIdent(tableName))
                .append(where.sql());
        if (!groupCols.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupCols));
        }
        sql.append(buildOrderBy(ir, resolvedMetrics, groupKeys));
        sql.append(" LIMIT ?");

        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        String finalSql = sql.toString();
        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(finalSql, args.toArray());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> db : dbRows) {
            Map<String, Object> api = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : db.entrySet()) {
                api.put(e.getKey(), e.getValue());
            }
            rows.add(maskSensitive(api, sensitiveFields));
        }
        return new QueryRawResult(rows, rows.size(), finalSql, System.currentTimeMillis() - start);
    }

    /**
     * 字段画像：各维度 Top 值
     */
    public Map<String, Object> buildProfile(String tableName, DataModelSchema schema,
                                            List<AskDimensionDef> dimensions) {
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        List<AskDimensionDef> dims = dimensions != null ? dimensions : List.of();
        for (AskDimensionDef dim : dims) {
            if (!StringUtils.hasText(dim.getFieldKey())) {
                continue;
            }
            try {
                String col = resolveColumn(schema, dim.getFieldKey());
                String sql = "SELECT " + schemaSupport.quoteIdent(col) + "::text AS v, COUNT(1) AS c FROM "
                        + schemaSupport.quoteIdent(tableName)
                        + " WHERE " + schemaSupport.quoteIdent("deleted") + " = 0 AND "
                        + schemaSupport.quoteIdent(col) + " IS NOT NULL GROUP BY 1 ORDER BY c DESC LIMIT 8";
                List<Map<String, Object>> tops = jdbcTemplate.queryForList(sql);
                Map<String, Object> fieldProfile = new LinkedHashMap<>();
                fieldProfile.put("topValues", tops);
                fields.put(dim.getFieldKey(), fieldProfile);
            } catch (Exception e) {
                log.warn("[AskData] 画像字段失败: field={}, err={}", dim.getFieldKey(), e.getMessage());
            }
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + schemaSupport.quoteIdent(tableName)
                        + " WHERE " + schemaSupport.quoteIdent("deleted") + " = 0",
                Long.class);
        profile.put("totalRows", total != null ? total : 0);
        profile.put("fields", fields);
        profile.put("refreshedAt", java.time.Instant.now().toString());
        return profile;
    }

    private List<AskMetricDef> resolveMetrics(List<AskMetricDef> catalog, AskDataIntentIR ir) {
        List<String> codes = Optional.ofNullable(ir.getMetrics()).orElseGet(List::of);
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, AskMetricDef> byCode = new LinkedHashMap<>();
        if (catalog != null) {
            for (AskMetricDef m : catalog) {
                if (m.getCode() != null) {
                    byCode.put(m.getCode().toLowerCase(Locale.ROOT), m);
                }
            }
        }
        List<AskMetricDef> out = new ArrayList<>();
        for (String code : codes) {
            AskMetricDef m = byCode.get(code.toLowerCase(Locale.ROOT));
            if (m == null) {
                // 允许临时聚合：field:op 或 裸字段 sum
                AskMetricDef adhoc = parseAdhocMetric(code);
                if (adhoc == null) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "未知指标: " + code);
                }
                out.add(adhoc);
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private AskMetricDef parseAdhocMetric(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        if ("count".equalsIgnoreCase(code) || "cnt".equalsIgnoreCase(code)) {
            AskMetricDef m = new AskMetricDef();
            m.setCode("cnt");
            m.setOp("count");
            m.setName("数量");
            return m;
        }
        // sum:amount
        int idx = code.indexOf(':');
        if (idx > 0) {
            String op = code.substring(0, idx).toLowerCase(Locale.ROOT);
            String field = code.substring(idx + 1);
            if (METRIC_OPS.contains(op) && StringUtils.hasText(field)) {
                AskMetricDef m = new AskMetricDef();
                m.setCode(sanitizeAlias(code.replace(':', '_')));
                m.setOp(op);
                m.setField(field);
                m.setName(code);
                return m;
            }
        }
        return null;
    }

    private SqlParts buildWhere(DataModelSchema schema, List<AskDimensionDef> dimensions,
                                AskDataIntentIR ir, List<AskFilterDef> defaultFilters) {
        StringBuilder where = new StringBuilder(" WHERE ")
                .append(schemaSupport.quoteIdent("deleted")).append(" = 0");
        List<Object> args = new ArrayList<>();

        // 默认过滤 + IR 过滤（指标固化过滤已由调用方合并进 ir.filters）
        List<AskDataIntentIR.AskDataFilter> all = new ArrayList<>();
        if (defaultFilters != null) {
            for (AskFilterDef df : defaultFilters) {
                if (df == null || !StringUtils.hasText(df.getField())) {
                    continue;
                }
                AskDataIntentIR.AskDataFilter f = new AskDataIntentIR.AskDataFilter();
                f.setField(df.getField());
                f.setOp(df.getOp());
                f.setValue(df.getValue());
                all.add(f);
            }
        }
        if (ir.getFilters() != null) {
            all.addAll(ir.getFilters());
        }

        if (StringUtils.hasText(ir.getKeyword())) {
            List<String> fuzzy = Optional.ofNullable(schema.getFuzzySearchFields()).orElseGet(List::of);
            List<String> likes = new ArrayList<>();
            for (String key : fuzzy) {
                if (schemaSupport.isSystemFieldKey(key) && !"createTime".equals(key) && !"updateTime".equals(key)) {
                    continue;
                }
                try {
                    String col = resolveColumn(schema, key);
                    likes.add(schemaSupport.quoteIdent(col) + "::text ILIKE ?");
                    args.add("%" + ir.getKeyword().trim() + "%");
                } catch (Exception ignored) {
                    // skip invalid
                }
            }
            if (!likes.isEmpty()) {
                where.append(" AND (").append(String.join(" OR ", likes)).append(")");
            }
        }

        for (AskDataIntentIR.AskDataFilter f : all) {
            String field = StringUtils.hasText(f.getDim()) ? f.getDim() : f.getField();
            if (!StringUtils.hasText(field)) {
                continue;
            }
            String op = StringUtils.hasText(f.getOp()) ? f.getOp().toLowerCase(Locale.ROOT) : "eq";
            if (!FILTER_OPS.contains(op)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的过滤算子: " + op);
            }
            String col = resolveColumn(schema, field);
            String q = schemaSupport.quoteIdent(col);
            switch (op) {
                case "eq" -> {
                    where.append(" AND ").append(q).append(" = ?");
                    args.add(f.getValue());
                }
                case "ne" -> {
                    where.append(" AND ").append(q).append(" <> ?");
                    args.add(f.getValue());
                }
                case "gt" -> {
                    where.append(" AND ").append(q).append(" > ?");
                    args.add(f.getValue());
                }
                case "gte" -> {
                    where.append(" AND ").append(q).append(" >= ?");
                    args.add(f.getValue());
                }
                case "lt" -> {
                    where.append(" AND ").append(q).append(" < ?");
                    args.add(f.getValue());
                }
                case "lte" -> {
                    where.append(" AND ").append(q).append(" <= ?");
                    args.add(f.getValue());
                }
                case "like" -> {
                    where.append(" AND ").append(q).append("::text ILIKE ?");
                    args.add("%" + String.valueOf(f.getValue()) + "%");
                }
                case "not_like" -> {
                    where.append(" AND (").append(q).append(" IS NULL OR ")
                            .append(q).append("::text NOT ILIKE ?)");
                    args.add("%" + String.valueOf(f.getValue()) + "%");
                }
                case "starts_with" -> {
                    where.append(" AND ").append(q).append("::text ILIKE ?");
                    args.add(String.valueOf(f.getValue()) + "%");
                }
                case "not_starts_with" -> {
                    where.append(" AND (").append(q).append(" IS NULL OR ")
                            .append(q).append("::text NOT ILIKE ?)");
                    args.add(String.valueOf(f.getValue()) + "%");
                }
                case "in" -> {
                    List<?> list = toList(f.getValue());
                    if (list.isEmpty()) {
                        where.append(" AND 1=0");
                    } else {
                        where.append(" AND ").append(q).append(" IN (")
                                .append(String.join(",", Collections.nCopies(list.size(), "?")))
                                .append(")");
                        args.addAll(list);
                    }
                }
                case "not_in" -> {
                    List<?> list = toList(f.getValue());
                    if (list.isEmpty()) {
                        // 空集合：不属于任何值 → 恒真
                        where.append(" AND 1=1");
                    } else {
                        where.append(" AND (").append(q).append(" IS NULL OR ")
                                .append(q).append(" NOT IN (")
                                .append(String.join(",", Collections.nCopies(list.size(), "?")))
                                .append("))");
                        args.addAll(list);
                    }
                }
                case "between" -> {
                    List<?> list = toList(f.getValue());
                    if (list.size() < 2) {
                        throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "between 需要两个值");
                    }
                    where.append(" AND ").append(q).append(" BETWEEN ? AND ?");
                    args.add(list.get(0));
                    args.add(list.get(1));
                }
                case "is_null" -> where.append(" AND ").append(q).append(" IS NULL");
                case "is_not_null" -> where.append(" AND ").append(q).append(" IS NOT NULL");
                case "in_last" -> appendInLast(where, args, q, f.getValue());
                default -> throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的过滤算子: " + op);
            }
        }
        return new SqlParts(where.toString(), args);
    }

    private void appendInLast(StringBuilder where, List<Object> args, String quotedCol, Object value) {
        int n = 1;
        String unit = "day";
        if (value instanceof Map<?, ?> map) {
            Object nObj = map.get("n");
            Object uObj = map.get("unit");
            if (nObj instanceof Number num) {
                n = Math.max(1, num.intValue());
            } else if (nObj != null) {
                n = Math.max(1, Integer.parseInt(String.valueOf(nObj)));
            }
            if (uObj != null) {
                unit = String.valueOf(uObj).toLowerCase(Locale.ROOT);
            }
        }
        String pgUnit = switch (unit) {
            case "week", "weeks" -> "week";
            case "month", "months" -> "month";
            case "year", "years" -> "year";
            default -> "day";
        };
        where.append(" AND ").append(quotedCol).append(" >= (CURRENT_TIMESTAMP - (? || ' ")
                .append(pgUnit).append("')::interval)");
        args.add(String.valueOf(n));
    }

    private void appendEq(StringBuilder where, List<Object> args, DataModelSchema schema,
                          String field, Object value) {
        if (value == null || "".equals(value)) {
            return;
        }
        String col = resolveColumn(schema, field);
        where.append(" AND ").append(schemaSupport.quoteIdent(col)).append(" = ?");
        args.add(value);
    }

    private String metricExpr(AskMetricDef m, DataModelSchema schema) {
        String op = m.getOp() != null ? m.getOp().toLowerCase(Locale.ROOT) : "count";
        if (!METRIC_OPS.contains(op)) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的聚合: " + op);
        }
        if ("count".equals(op) && !StringUtils.hasText(m.getField())) {
            return "COUNT(1)";
        }
        if (!StringUtils.hasText(m.getField())) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标缺少字段: " + m.getCode());
        }
        String col = schemaSupport.quoteIdent(resolveColumn(schema, m.getField()));
        return switch (op) {
            case "count" -> "COUNT(" + col + ")";
            case "count_distinct" -> "COUNT(DISTINCT " + col + ")";
            case "sum" -> "SUM((" + col + ")::numeric)";
            case "avg" -> "AVG((" + col + ")::numeric)";
            case "min" -> "MIN(" + col + ")";
            case "max" -> "MAX(" + col + ")";
            default -> throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的聚合: " + op);
        };
    }

    private String buildOrderBy(AskDataIntentIR ir, List<AskMetricDef> metrics, List<String> groupKeys) {
        List<AskDataIntentIR.AskDataOrderBy> orders = Optional.ofNullable(ir.getOrderBy()).orElseGet(List::of);
        if (orders.isEmpty()) {
            if (!metrics.isEmpty()) {
                return " ORDER BY " + schemaSupport.quoteIdent(sanitizeAlias(metrics.get(0).getCode())) + " DESC";
            }
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (AskDataIntentIR.AskDataOrderBy o : orders) {
            String key = firstNonBlank(o.getMetric(), o.getField(), o.getDim());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String dir = "asc".equalsIgnoreCase(o.getDir()) ? "ASC" : "DESC";
            parts.add(schemaSupport.quoteIdent(sanitizeAlias(key)) + " " + dir);
        }
        return parts.isEmpty() ? "" : " ORDER BY " + String.join(", ", parts);
    }

    private String timeBucketExpr(String col, String grain) {
        String g = grain != null ? grain.toLowerCase(Locale.ROOT) : "day";
        String trunc = switch (g) {
            case "week" -> "week";
            case "month" -> "month";
            default -> "day";
        };
        return "date_trunc('" + trunc + "', " + schemaSupport.quoteIdent(col) + ")";
    }

    private String resolveDefaultTimeField(List<AskDimensionDef> dimensions, AskDataIntentIR ir) {
        if (dimensions != null) {
            for (AskDimensionDef d : dimensions) {
                if ("time".equalsIgnoreCase(d.getType())) {
                    return d.getFieldKey();
                }
            }
        }
        return "createTime";
    }

    private AskDimensionDef findDimension(List<AskDimensionDef> dimensions, String key) {
        if (dimensions == null || key == null) {
            return null;
        }
        for (AskDimensionDef d : dimensions) {
            if (key.equals(d.getFieldKey()) || key.equalsIgnoreCase(d.getName())) {
                return d;
            }
            if (d.getSynonyms() != null) {
                for (String s : d.getSynonyms()) {
                    if (key.equalsIgnoreCase(s)) {
                        return d;
                    }
                }
            }
        }
        return null;
    }

    private String resolveColumn(DataModelSchema schema, String fieldKey) {
        if ("id".equals(fieldKey)) {
            return "id";
        }
        if ("createTime".equals(fieldKey) || "create_time".equals(fieldKey)) {
            return "create_time";
        }
        if ("updateTime".equals(fieldKey) || "update_time".equals(fieldKey)) {
            return "update_time";
        }
        // 校验自定义字段存在
        boolean found = schemaSupport.customFields(schema).stream()
                .anyMatch(f -> fieldKey.equals(f.getKey()));
        if (!found && !schemaSupport.isSystemFieldKey(fieldKey)) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "未知字段: " + fieldKey);
        }
        return schemaSupport.toColumnName(fieldKey);
    }

    private Map<String, Object> toApiRow(Map<String, Object> dbRow, DataModelSchema schema) {
        Map<String, Object> api = new DataPoolRecordMap();
        Object id = dbRow.get("id");
        api.put("id", id != null ? String.valueOf(id) : null);
        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            String col = schemaSupport.toColumnName(f.getKey());
            Object val = dbRow.containsKey(col) ? dbRow.get(col) : dbRow.get(col.toLowerCase(Locale.ROOT));
            api.put(f.getKey(), val);
        }
        Object createTime = dbRow.get("create_time") != null ? dbRow.get("create_time") : dbRow.get("createTime");
        Object updateTime = dbRow.get("update_time") != null ? dbRow.get("update_time") : dbRow.get("updateTime");
        api.put("createTime", createTime != null ? String.valueOf(createTime) : null);
        api.put("updateTime", updateTime != null ? String.valueOf(updateTime) : null);
        return api;
    }

    private Map<String, Object> maskSensitive(Map<String, Object> row, List<String> sensitiveFields) {
        if (sensitiveFields == null || sensitiveFields.isEmpty()) {
            return row;
        }
        for (String f : sensitiveFields) {
            if (row.containsKey(f) && row.get(f) != null) {
                row.put(f, "***");
            }
        }
        return row;
    }

    private String sanitizeAlias(String raw) {
        String s = raw == null ? "col" : raw.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            s = "c_" + s;
        }
        if (s.length() > 60) {
            s = s.substring(0, 60);
        }
        return s;
    }

    private List<?> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value.getClass().isArray()) {
            return Arrays.asList((Object[]) value);
        }
        return List.of(value);
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
