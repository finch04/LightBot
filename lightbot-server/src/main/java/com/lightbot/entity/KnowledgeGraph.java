package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.GraphTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识图谱（知识库级别，1:1）
 *
 * @author finch
 * @since 2026-05-30
 */
@Data
@TableName("knowledge_graph")
@Schema(description = "知识图谱")
public class KnowledgeGraph {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("knowledge_id")
    @Schema(description = "知识库ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField("status")
    @Schema(description = "图谱状态")
    private GraphTaskStatus status;

    @TableField("node_count")
    @Schema(description = "节点总数")
    private Integer nodeCount;

    @TableField("edge_count")
    @Schema(description = "边总数")
    private Integer edgeCount;

    @TableField("task_id")
    @Schema(description = "当前正在运行的异步任务ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

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
