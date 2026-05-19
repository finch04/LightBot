package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.CommonStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill表(Agent技能)
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("skill")
public class Skill {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属AgentID */
    private Long agentId;

    /** 关联ToolID，可为空表示纯Prompt技能 */
    private Long toolId;

    /** 技能名称 */
    private String name;

    /** 技能描述 */
    private String description;

    /** 技能提示词模板 */
    private String promptTemplate;

    /** 扩展配置(JSON) */
    private String config;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态: active-启用, disabled-禁用 */
    private CommonStatus status;

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
