package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.CommonStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 知识库表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("knowledge")
@Schema(description = "知识库表")
public class Knowledge {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    private Long userId;

    @TableField("name")
    @Schema(description = "知识库名称")
    private String name;

    @TableField("description")
    @Schema(description = "知识库描述")
    private String description;

    @TableField("embedding_model")
    @Schema(description = "向量化模型名称")
    private String embeddingModel;

    @TableField("chunk_size")
    @Schema(description = "分块大小")
    private Integer chunkSize;

    @TableField("chunk_overlap")
    @Schema(description = "分块重叠")
    private Integer chunkOverlap;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField("document_count")
    @Schema(description = "文档总数")
    private Integer documentCount;

    @TableField("chunk_count")
    @Schema(description = "分块总数")
    private Integer chunkCount;

    @TableField("total_tokens")
    @Schema(description = "总Token数")
    private Long totalTokens;

    @TableField("status")
    @Schema(description = "状态")
    private CommonStatus status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
