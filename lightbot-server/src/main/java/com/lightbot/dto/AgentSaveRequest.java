package com.lightbot.dto;

import com.lightbot.enums.AgentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 创建/更新请求 DTO
 * <p>仅包含用户可编辑的字段，防止客户端设置 id、userId、status 等内部字段</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@Schema(description = "Agent创建/更新请求")
public class AgentSaveRequest {

    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    @Size(max = 50, message = "Agent名称不超过50字")
    @Schema(description = "Agent名称")
    private String name;

    @Size(max = 50, message = "Agent描述不超过50字")
    @Schema(description = "Agent描述")
    private String description;

    @Size(max = 2000, message = "系统提示词不超过2000字")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Size(max = 200, message = "欢迎语不超过200字")
    @Schema(description = "欢迎语")
    private String welcomeMessage;

    @Schema(description = "推荐问题列表（JSON数组）")
    private String recommendedQuestions;

    @Schema(description = "头像存储路径")
    private String avatar;

    @Schema(description = "Agent图标")
    private String icon;

    @Schema(description = "类型")
    private AgentType agentType;

    @Schema(description = "扩展配置（JSON）")
    private String config;
}
