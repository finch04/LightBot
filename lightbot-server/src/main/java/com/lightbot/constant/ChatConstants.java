package com.lightbot.constant;

/**
 * 对话运行时通用常量
 *
 * @author finch
 * @since 2026-06-30
 */
public final class ChatConstants {

    private ChatConstants() {}

    /** 工具执行墙钟超时（秒）：内置 30s、API 60s、MCP/SubAgent 120s，统一取最大值 */
    public static final long TOOL_EXECUTION_TIMEOUT_SECONDS = 120;
}
