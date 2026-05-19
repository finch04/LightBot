package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.ContentType;
import com.lightbot.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("message")
public class Message {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话ID */
    private Long sessionId;

    /** 角色: user-用户, assistant-助手, system-系统, tool-工具 */
    private MessageRole role;

    /** 消息内容 */
    private String content;

    /** 内容类型: text-文本, image-图片, file-文件 */
    private ContentType contentType;

    /** 工具调用列表(JSON) */
    private String toolCalls;

    /** 工具调用ID，用于tool角色消息 */
    private String toolCallId;

    /** Token数量 */
    private Integer tokenCount;

    /** 元数据(JSON)，含模型名、耗时等 */
    private String metadata;

    /** 父消息ID，用于分支对话 */
    private Long parentId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
