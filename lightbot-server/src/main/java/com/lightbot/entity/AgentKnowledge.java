package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent-知识库关联表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("agent_knowledge")
@Schema(description = "Agent-知识库关联表")
public class AgentKnowledge {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("agent_id")
    @Schema(description = "Agent ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @TableField("knowledge_id")
    @Schema(description = "知识库 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
