package com.lightbot.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PostgreSQL UUID类型处理器
 * 用于处理Java UUID类型与PostgreSQL数据库UUID类型之间的转换
 */
@MappedTypes(UUID.class)
public class PostgreSQLUUIDTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 设置非空参数到PreparedStatement中
     *
     * @param ps        PreparedStatement对象
     * @param i         参数索引
     * @param parameter UUID参数值
     * @param jdbcType  JDBC类型
     * @throws SQLException SQL异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        // 使用 setObject 是关键，让 JDBC 驱动去处理转换
        ps.setObject(i, parameter);
    }

    /**
     * 从ResultSet中获取可为空的UUID结果
     *
     * @param rs         ResultSet对象
     * @param columnName 列名
     * @return UUID结果值
     * @throws SQLException SQL异常
     */
    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 使用 getObject 获取 UUID 对象
        return rs.getObject(columnName, UUID.class); // 或者直接 (UUID) rs.getObject(columnName)
    }

    /**
     * 从ResultSet中获取可为空的UUID结果
     *
     * @param rs          ResultSet对象
     * @param columnIndex 列索引
     * @return UUID结果值
     * @throws SQLException SQL异常
     */
    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, UUID.class);
    }

    /**
     * 从CallableStatement中获取可为空的UUID结果
     *
     * @param cs          CallableStatement对象
     * @param columnIndex 列索引
     * @return UUID结果值
     * @throws SQLException SQL异常
     */
    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getObject(columnIndex, UUID.class);
    }
}