package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SSE 断线重连请求
 *
 * @author finch
 */
@Data
public class ReconnectRequest {

    /** 原始 SSE 流的 requestId */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 前端最后收到的事件 ID（null 表示获取全部） */
    private Integer lastEventId;
}
