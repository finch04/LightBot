package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private Long sessionId;

    private Long agentId;

    /**
     * 入参变量，用于替换系统提示词中的 {{变量名}} 占位符
     */
    private Map<String, Object> bizParams;

    /**
     * 对话使用的配置版本：null=默认（已发布则用线上最新，否则 agent 表当前值）；
     * 0=暂存草稿；正整数=指定已发布版本号（用于调试/对比）
     */
    private Integer configVersion;
}
