package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 数据模型元数据（物理数据池表按 table_name 动态创建）
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
@TableName(value = "data_model", autoResultMap = true)
@Schema(description = "数据模型")
public class DataModel {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "所属用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("category_id")
    @Schema(description = "分类ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    @TableField("name")
    @Schema(description = "模型名称")
    private String name;

    @TableField("table_name")
    @Schema(description = "物理表名（前缀 sjc_data_）")
    private String tableName;

    @TableField("description")
    @Schema(description = "描述")
    private String description;

    @TableField(value = "schema_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "表单与约束配置 JSON")
    private String schemaJson;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
