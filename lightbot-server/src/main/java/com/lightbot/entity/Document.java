package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 文档表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("document")
@Schema(description = "文档表")
public class Document {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("user_id")
    @Schema(description = "上传者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Schema(description = "文档名称")
    private String name;

    @TableField("file_path")
    @Schema(description = "文件存储路径")
    private String filePath;

    @TableField("file_type")
    @Schema(description = "文件类型")
    private String fileType;

    @TableField("file_size")
    @Schema(description = "文件大小")
    private Long fileSize;

    @TableField("file_hash")
    @Schema(description = "文件哈希")
    private String fileHash;

    @TableField("chunk_count")
    @Schema(description = "分块数量")
    private Integer chunkCount;

    @TableField("token_count")
    @Schema(description = "Token数量")
    private Long tokenCount;

    @TableField("status")
    @Schema(description = "状态")
    private DocumentStatus status;

    @TableField("error_message")
    @Schema(description = "错误信息")
    private String errorMessage;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "文档元数据")
    private String metadata;

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
