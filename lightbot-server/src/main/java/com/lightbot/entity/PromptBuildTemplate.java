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
 * 提示词构建模板表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("prompt_build_template")
@Schema(description = "提示词构建模板表")
public class PromptBuildTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("prompt_template_key")
    @Schema(description = "模板唯一标识")
    private String promptTemplateKey;

    @TableField("tags")
    @Schema(description = "标签")
    private String tags;

    @TableField("template_desc")
    @Schema(description = "模板描述")
    private String templateDesc;

    @TableField("template")
    @Schema(description = "模板内容")
    private String template;

    @TableField("variables")
    @Schema(description = "变量定义")
    private String variables;

    @TableField(value = "model_config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "模型配置")
    private String modelConfig;

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
