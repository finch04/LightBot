package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.AgentType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("agent")
public class Agent {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建者ID */
    private Long userId;

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 头像URL */
    private String avatar;

    /** 类型: chat-对话型, workflow-工作流型, assistant-助手型 */
    private AgentType agentType;

    /** 扩展配置(JSON)，含 model_id、temperature、tools 等 */
    private String config;

    /** 状态: draft-草稿, published-已发布, archived-已归档 */
    private AgentStatus status;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 版本号，每次发布递增 */
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
