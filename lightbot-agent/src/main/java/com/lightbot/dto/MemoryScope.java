package com.lightbot.dto;

import com.lightbot.util.ExternalUserIdUtil;
import lombok.Data;

/**
 * 长期记忆命名空间
 * <p>
 * 控制台调试：userId + externalUserId=debug_user_{userId}（无 apiKeyId）；
 * 开放 API：apiKeyId + 业务 externalUserId。
 * </p>
 *
 * @author finch
 * @since 2026-08-01
 */
@Data
public class MemoryScope {

    /** 平台登录用户 ID；开放 API 记忆可为空 */
    private Long userId;

    /** 企业 API Key ID（开放 API 记忆必填） */
    private Long apiKeyId;

    /** 上层业务终端用户标识，或控制台调试标识 debug_user_{userId} */
    private String externalUserId;

    public boolean isExternal() {
        return apiKeyId != null
                && externalUserId != null
                && !externalUserId.isBlank();
    }

    /** 控制台调试命名空间（与开放 API Key 记忆隔离） */
    public boolean isConsoleDebug() {
        return userId != null
                && apiKeyId == null
                && ExternalUserIdUtil.isConsoleDebugId(externalUserId);
    }

    public boolean isValid() {
        return isExternal() || isConsoleDebug() || userId != null;
    }

    public static MemoryScope platform(Long userId) {
        MemoryScope scope = new MemoryScope();
        scope.setUserId(userId);
        return scope;
    }

    /**
     * 控制台调试记忆：debug_user_{建设者用户ID}
     *
     * @param userId 当前登录建设者 ID
     * @return 调试命名空间
     */
    public static MemoryScope consoleDebug(Long userId) {
        MemoryScope scope = new MemoryScope();
        scope.setUserId(userId);
        scope.setExternalUserId(ExternalUserIdUtil.consoleDebugId(userId));
        return scope;
    }

    public static MemoryScope external(Long apiKeyId, String externalUserId) {
        MemoryScope scope = new MemoryScope();
        scope.setApiKeyId(apiKeyId);
        scope.setExternalUserId(externalUserId != null ? externalUserId.trim() : null);
        return scope;
    }
}
