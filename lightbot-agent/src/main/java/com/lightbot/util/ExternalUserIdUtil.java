package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;

import java.util.regex.Pattern;

/**
 * 上层业务终端用户标识校验
 *
 * @author finch
 * @since 2026-08-01
 */
public final class ExternalUserIdUtil {

    public static final int MAX_LENGTH = 128;

    /** 控制台调试记忆前缀：debug_user_{建设者用户ID}，与开放 API 命名空间隔离 */
    public static final String CONSOLE_DEBUG_PREFIX = "debug_user_";

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._\\-:@/]{1,128}$");

    private ExternalUserIdUtil() {
    }

    /**
     * 规范化：空白视为未传；非空则校验格式
     *
     * @param raw 原始值
     * @return 规范化后的 ID，或 null
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.API_EXTERNAL_USER_INVALID);
        }
        return value;
    }

    /**
     * 控制台调试用外部用户标识：debug_user_{userId}
     *
     * @param userId 当前登录建设者用户 ID
     * @return 调试标识；userId 为空时返回 null
     */
    public static String consoleDebugId(Long userId) {
        if (userId == null) {
            return null;
        }
        return CONSOLE_DEBUG_PREFIX + userId;
    }

    /**
     * 是否为控制台调试标识
     *
     * @param externalUserId 外部用户标识
     * @return true 表示为控制台调试命名空间
     */
    public static boolean isConsoleDebugId(String externalUserId) {
        return externalUserId != null && externalUserId.startsWith(CONSOLE_DEBUG_PREFIX);
    }
}
