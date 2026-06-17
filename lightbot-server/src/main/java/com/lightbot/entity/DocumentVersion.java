package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本历史实体
 *
 * @author finch
 * @since 2026-06-17
 */
@Data
@TableName("document_version")
@Schema(description = "文档版本历史")
public class DocumentVersion {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("document_id")
    @Schema(description = "文档ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @TableField("version")
    @Schema(description = "版本号")
    private Integer version;

    @TableField("content_hash")
    @Schema(description = "内容MD5哈希")
    private String contentHash;

    @TableField("storage_path")
    @Schema(description = "MinIO存储路径")
    private String storagePath;

    @TableField("created_by")
    @Schema(description = "创建者用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @TableField("create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
