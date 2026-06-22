package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * 系统内置工具 — PostgreSQL 数据库查询
 * <p>提供只读数据库查询能力，包括列出表、查看表结构、执行 SELECT 查询</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("pgSqlTool")
@SystemTool(displayName = "数据库查询工具集", description = "PostgreSQL 数据库只读查询工具", tags = {"数据库"})
@RequiredArgsConstructor
public class PgSqlTool {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private static final int MAX_ROWS = 50;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final Pattern SAFE_SQL_PATTERN = Pattern.compile(
            "^\\s*(SELECT|SHOW|EXPLAIN|WITH)\\s",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE);

    @SystemTool(displayName = "列出数据库表")
    @Tool(name = "pg_list_tables",
          description = "列出数据库中所有表名。当用户想了解数据库有哪些表时调用此工具。")
    public String listTables() {
        log.info("[Tool:pg_list_tables] 列出所有表");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("tablename"));
            }
            if (tables.isEmpty()) return "数据库中没有表";

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("tables", tables);
            output.put("total", tables.size());
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[Tool:pg_list_tables] 查询异常: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    @SystemTool(displayName = "查看表结构")
    @Tool(name = "pg_describe_table",
          description = "查看指定表的结构，包括字段名、类型、是否可空、默认值、注释和索引信息。")
    public String describeTable(
            @ToolParam(description = "表名")
            @ToolParamMeta(example = "agent") String tableName) {
        log.info("[Tool:pg_describe_table] 查看表结构: tableName={}", tableName);

        // 校验表名合法性
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return "非法表名: " + tableName;
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("table_name", tableName);

        // 字段信息
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT c.column_name, c.data_type, c.is_nullable, c.column_default, " +
                             "pgd.description AS column_comment " +
                             "FROM information_schema.columns c " +
                             "LEFT JOIN pg_catalog.pg_statio_all_tables st " +
                             "ON st.schemaname = c.table_schema AND st.relname = c.table_name " +
                             "LEFT JOIN pg_catalog.pg_description pgd " +
                             "ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position " +
                             "WHERE c.table_schema = 'public' AND c.table_name = '" + tableName + "' " +
                             "ORDER BY c.ordinal_position")) {
            List<Map<String, Object>> columns = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("column_name", rs.getString("column_name"));
                col.put("data_type", rs.getString("data_type"));
                col.put("is_nullable", "YES".equals(rs.getString("is_nullable")));
                col.put("column_default", rs.getString("column_default"));
                col.put("column_comment", rs.getString("column_comment"));
                columns.add(col);
            }
            output.put("columns", columns);
        } catch (Exception e) {
            return "查询字段信息失败: " + e.getMessage();
        }

        // 索引信息
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '" + tableName + "'")) {
            List<Map<String, Object>> indexes = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> idx = new LinkedHashMap<>();
                idx.put("index_name", rs.getString("indexname"));
                idx.put("index_def", rs.getString("indexdef"));
                indexes.add(idx);
            }
            output.put("indexes", indexes);
        } catch (Exception e) {
            output.put("indexes", List.of());
        }

        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "序列化失败: " + e.getMessage();
        }
    }

    @SystemTool(displayName = "执行SQL查询")
    @Tool(name = "pg_query",
          description = "执行只读 SQL 查询（仅允许 SELECT）。当用户需要查询数据库中的数据时调用此工具。")
    public String query(
            @ToolParam(description = "SQL 查询语句（仅 SELECT）")
            @ToolParamMeta(example = "SELECT * FROM agent LIMIT 10") String sql) {
        log.info("[Tool:pg_query] 执行查询: sql={}", sql);

        // SQL 安全校验
        String validationError = validateSql(sql);
        if (validationError != null) return validationError;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setMaxRows(MAX_ROWS);
            long startTime = System.currentTimeMillis();
            ResultSet rs = stmt.executeQuery(sql);
            long elapsed = System.currentTimeMillis() - startTime;

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            // 收集列名
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                headers.add(meta.getColumnLabel(i));
            }

            // 收集数据行
            List<String[]> rows = new ArrayList<>();
            int totalRows = 0;
            while (rs.next() && rows.size() < MAX_ROWS) {
                String[] row = new String[colCount];
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    row[i - 1] = val != null ? val.toString() : "NULL";
                }
                rows.add(row);
                totalRows++;
            }

            // 检查是否有更多行
            boolean hasMore = rs.next();
            rs.close();

            if (rows.isEmpty()) {
                Map<String, Object> emptyOutput = new LinkedHashMap<>();
                emptyOutput.put("sql", sql);
                emptyOutput.put("columns", headers);
                emptyOutput.put("rows", List.of());
                emptyOutput.put("total_rows", 0);
                emptyOutput.put("has_more", false);
                emptyOutput.put("elapsed_ms", elapsed);
                return objectMapper.writeValueAsString(emptyOutput);
            }

            // 构建 JSON 返回
            List<List<String>> rowList = new ArrayList<>();
            for (String[] row : rows) {
                List<String> rowItems = new ArrayList<>();
                for (String val : row) {
                    rowItems.add(val.length() > 200 ? val.substring(0, 197) + "..." : val);
                }
                rowList.add(rowItems);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sql", sql);
            output.put("columns", headers);
            output.put("rows", rowList);
            output.put("total_rows", totalRows);
            output.put("has_more", hasMore);
            output.put("elapsed_ms", elapsed);
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[Tool:pg_query] 查询异常: sql={}, error={}", sql, e.getMessage());
            String msg = e.getMessage();
            if (msg != null && msg.contains("does not exist")) {
                return "查询失败: 表或列不存在 — " + msg;
            }
            return "查询失败: " + msg;
        }
    }

    /**
     * SQL 安全校验：仅允许只读查询
     */
    private String validateSql(String sql) {
        if (sql == null || sql.isBlank()) return "SQL 不能为空";
        String trimmed = sql.strip();
        if (!SAFE_SQL_PATTERN.matcher(trimmed).find()) {
            return "仅允许 SELECT/SHOW/EXPLAIN 查询，不允许写操作";
        }
        if (DANGEROUS_KEYWORDS.matcher(trimmed).find()) {
            return "SQL 中包含危险关键词（INSERT/UPDATE/DELETE/DROP 等），仅允许只读查询";
        }
        return null;
    }
}
