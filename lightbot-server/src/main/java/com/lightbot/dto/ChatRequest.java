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
}
