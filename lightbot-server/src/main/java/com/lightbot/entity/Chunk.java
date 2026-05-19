package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 文档分块表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("chunk")
@Schema(description = "文档分块表")
public class Chunk {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("document_id")
    @Schema(description = "文档ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("content")
    @Schema(description = "分块内容")
    private String content;

    @TableField("chunk_index")
    @Schema(description = "分块序号")
    private Integer chunkIndex;

    @TableField("token_count")
    @Schema(description = "Token数量")
    private Integer tokenCount;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "分块元数据")
    private String metadata;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
