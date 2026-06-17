package com.lightbot.tool.builtin;

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
import java.util.List;
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
            return "数据库表列表（共 " + tables.size() + " 张表）：\n" + String.join("\n", tables);
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

        StringBuilder sb = new StringBuilder();
        sb.append("表结构: ").append(tableName).append("\n\n");

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
            sb.append("字段：\n");
            sb.append(String.format("%-25s %-20s %-8s %-20s %s\n", "名称", "类型", "可空", "默认值", "注释"));
            sb.append("-".repeat(90)).append("\n");
            while (rs.next()) {
                sb.append(String.format("%-25s %-20s %-8s %-20s %s\n",
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        "YES".equals(rs.getString("is_nullable")) ? "Y" : "N",
                        rs.getString("column_default") != null ? rs.getString("column_default") : "-",
                        rs.getString("column_comment") != null ? rs.getString("column_comment") : "-"));
            }
        } catch (Exception e) {
            return "查询字段信息失败: " + e.getMessage();
        }

        // 索引信息
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = '" + tableName + "'")) {
            List<String> indexes = new ArrayList<>();
            while (rs.next()) {
                indexes.add(rs.getString("indexname") + ": " + rs.getString("indexdef"));
            }
            if (!indexes.isEmpty()) {
                sb.append("\n索引：\n");
                indexes.forEach(idx -> sb.append("  ").append(idx).append("\n"));
            }
        } catch (Exception e) {
            sb.append("\n查询索引信息失败: ").append(e.getMessage());
        }

        return sb.toString();
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

            if (rows.isEmpty()) return "查询结果为空（" + elapsed + "ms）";

            // 格式化为文本表格
            int[] widths = new int[colCount];
            for (int i = 0; i < colCount; i++) {
                widths[i] = headers.get(i).length();
                for (String[] row : rows) {
                    widths[i] = Math.min(Math.max(widths[i], row[i].length()), 40);
                }
            }

            StringBuilder sb = new StringBuilder();
            // 表头
            for (int i = 0; i < colCount; i++) {
                if (i > 0) sb.append(" | ");
                sb.append(String.format("%-" + widths[i] + "s", headers.get(i)));
            }
            sb.append("\n");
            for (int w : widths) sb.append("-".repeat(w)).append("---");
            sb.append("\n");

            // 数据行
            for (String[] row : rows) {
                for (int i = 0; i < colCount; i++) {
                    if (i > 0) sb.append(" | ");
                    String val = row[i].length() > 40 ? row[i].substring(0, 37) + "..." : row[i];
                    sb.append(String.format("%-" + widths[i] + "s", val));
                }
                sb.append("\n");
            }

            sb.append(String.format("\n共 %d 行", totalRows));
            if (hasMore) sb.append("（仅显示前 ").append(MAX_ROWS).append(" 行）");
            sb.append("，耗时 ").append(elapsed).append("ms");

            String result = sb.toString();
            if (result.length() > MAX_CONTENT_LENGTH) {
                result = result.substring(0, MAX_CONTENT_LENGTH) + "\n...（结果过长已截断）";
            }
            return result;
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
