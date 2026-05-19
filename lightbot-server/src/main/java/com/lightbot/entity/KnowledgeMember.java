package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.KnowledgeRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("knowledge_member")
@Schema(description = "知识库成员表")
public class KnowledgeMember {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @TableField("user_id")
    @Schema(description = "用户ID")
    private Long userId;

    @TableField("role")
    @Schema(description = "角色")
    private KnowledgeRole role;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
