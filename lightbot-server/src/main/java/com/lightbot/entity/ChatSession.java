package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** AgentID */
    private Long agentId;

    /** 用户ID */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 状态: active-活跃, archived-已归档 */
    private SessionStatus status;

    /** 会话上下文(JSON)，含变量、记忆窗口等 */
    private String context;

    /** 消息数量，冗余字段 */
    private Integer messageCount;

    /** 总Token消耗 */
    private Long totalTokens;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

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
