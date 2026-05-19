package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.CommonStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("knowledge")
public class Knowledge {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建者ID */
    private Long userId;

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 向量化模型名称 */
    private String embeddingModel;

    /** 分块大小(Token) */
    private Integer chunkSize;

    /** 分块重叠(Token) */
    private Integer chunkOverlap;

    /** 扩展配置(JSON)，含检索模式、top_k 等 */
    private String config;

    /** 文档总数，冗余字段 */
    private Integer documentCount;

    /** 分块总数，冗余字段 */
    private Integer chunkCount;

    /** 总Token数 */
    private Long totalTokens;

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
