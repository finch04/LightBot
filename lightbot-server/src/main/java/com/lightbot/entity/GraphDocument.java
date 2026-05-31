package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.GraphTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图谱文档关联（记录每个文档的图谱抽取状态）
 *
 * @author finch
 * @since 2026-05-30
 */
@Data
@TableName("graph_document")
@Schema(description = "图谱文档关联")
public class GraphDocument {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("graph_id")
    @Schema(description = "图谱ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long graphId;

    @TableField("document_id")
    @Schema(description = "文档ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @TableField("status")
    @Schema(description = "抽取状态")
    private GraphTaskStatus status;

    @TableField("entity_count")
    @Schema(description = "抽取实体数")
    private Integer entityCount;

    @TableField("relation_count")
    @Schema(description = "抽取关系数")
    private Integer relationCount;

    @TableField("error_message")
    @Schema(description = "失败原因")
    private String errorMessage;

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
