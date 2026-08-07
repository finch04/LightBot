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
 * 智能问数分析数据集
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@TableName(value = "ask_dataset", autoResultMap = true)
@Schema(description = "智能问数分析数据集")
public class AskDataset {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("data_model_id")
    @Schema(description = "绑定的数据模型ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dataModelId;

    @TableField("code")
    @Schema(description = "稳定编码")
    private String code;

    @TableField("name")
    @Schema(description = "显示名称")
    private String name;

    @TableField("description")
    @Schema(description = "描述")
    private String description;

    @TableField("default_time_field")
    @Schema(description = "默认时间字段 key")
    private String defaultTimeField;

    @TableField(value = "default_filters", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "默认过滤 JSON")
    private String defaultFilters;

    @TableField(value = "sensitive_fields", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "敏感字段 JSON 数组")
    private String sensitiveFields;

    @TableField(value = "dimensions", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "维度定义 JSON 数组")
    private String dimensions;

    @TableField(value = "metrics", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "指标定义 JSON 数组")
    private String metrics;

    @TableField(value = "profile_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "字段画像 JSON")
    private String profileJson;

    @TableField(value = "tenant_dimensions", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "租户维度映射 JSON：callerContext 键 → 表字段（如 regionId→region_id）")
    private String tenantDimensions;

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
