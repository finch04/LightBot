package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.NodeType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Workflow节点表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("workflow_node")
@Schema(description = "Workflow节点表")
public class WorkflowNode {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("workflow_id")
    @Schema(description = "工作流ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    @TableField("node_key")
    @Schema(description = "节点标识")
    private String nodeKey;

    @TableField("node_type")
    @Schema(description = "节点类型")
    private NodeType nodeType;

    @TableField("name")
    @Schema(description = "节点名称")
    private String name;

    @TableField("description")
    @Schema(description = "节点描述")
    private String description;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "节点配置")
    private String config;

    @TableField(value = "inputs", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "输入端口定义")
    private String inputs;

    @TableField(value = "outputs", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "输出端口定义")
    private String outputs;

    @TableField("position_x")
    @Schema(description = "画布X坐标")
    private Double positionX;

    @TableField("position_y")
    @Schema(description = "画布Y坐标")
    private Double positionY;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
