package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.NodeType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow节点表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("workflow_node")
public class WorkflowNode {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属工作流ID */
    private Long workflowId;

    /** 节点标识，Workflow内唯一 */
    private String nodeKey;

    /** 节点类型: start/end/llm/tool/condition/code */
    private NodeType nodeType;

    /** 节点名称 */
    private String name;

    /** 节点描述 */
    private String description;

    /** 节点配置(JSON) */
    private String config;

    /** 输入端口定义(JSON) */
    private String inputs;

    /** 输出端口定义(JSON) */
    private String outputs;

    /** 画布X坐标 */
    private Double positionX;

    /** 画布Y坐标 */
    private Double positionY;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
