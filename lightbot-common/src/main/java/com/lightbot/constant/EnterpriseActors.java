package com.lightbot.constant;

/**
 * 企业版虚拟身份常量
 * <p>API Key / 系统集成等非个人登录场景使用，避免将会话与记忆挂到创建人账号上</p>
 *
 * @author finch
 * @since 2026-07-30
 */
public final class EnterpriseActors {

    private EnterpriseActors() {
    }

    /**
     * 企业 API Key 调用使用的虚拟用户 ID（不对应 users 表真实账号）
     */
    public static final long API_KEY = 0L;

    /** 会话来源：平台调试 */
    public static final String SESSION_SOURCE_PLATFORM = "platform";

    /** 会话来源：企业 API Key 集成 */
    public static final String SESSION_SOURCE_API = "api";

    /** 会话来源：自动化任务 */
    public static final String SESSION_SOURCE_AUTOMATION = "automation";
}
