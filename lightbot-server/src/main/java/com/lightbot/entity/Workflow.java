package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.WorkflowStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("workflow")
public class Workflow {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联AgentID，可独立存在 */
    private Long agentId;

    /** 创建者ID */
    private Long userId;

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 图数据(JSON)，存储节点、边、布局信息 */
    private String graphData;

    /** 扩展配置(JSON) */
    private String config;

    /** 状态: draft-草稿, published-已发布, archived-已归档 */
    private WorkflowStatus status;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除 1-已删除 */
    @TableLogic
    private Integer deleted;
}
