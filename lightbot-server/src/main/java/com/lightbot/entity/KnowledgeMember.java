package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.KnowledgeRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员表
 * <p>控制用户对知识库的访问权限</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("knowledge_member")
public class KnowledgeMember {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 知识库ID */
    private Long knowledgeId;

    /** 用户ID */
    private Long userId;

    /** 角色: creator-创建者, manager-管理者, developer-开发者, viewer-查看者 */
    private KnowledgeRole role;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
