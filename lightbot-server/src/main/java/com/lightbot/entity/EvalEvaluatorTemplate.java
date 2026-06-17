package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 评估器模板表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_evaluator_template")
@Schema(description = "评估器模板表")
public class EvalEvaluatorTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("evaluator_template_key")
    @Size(max = 100, message = "模板标识不超过100字")
    @Schema(description = "模板唯一标识")
    private String evaluatorTemplateKey;

    @TableField("template_desc")
    @Size(max = 200, message = "模板描述不超过200字")
    @Schema(description = "模板描述")
    private String templateDesc;

    @TableField("template")
    @Size(max = 5000, message = "模板内容不超过5000字")
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
