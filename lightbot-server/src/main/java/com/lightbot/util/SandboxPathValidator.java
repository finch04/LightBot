package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;

import java.util.List;

/**
 * 沙箱路径安全校验工具
 * <p>防止路径遍历攻击（..），并校验读写白名单。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
public final class SandboxPathValidator {

    private SandboxPathValidator() {}

    private static final List<String> READABLE_ROOTS = List.of("skills/", "sessions/");
    private static final List<String> WRITABLE_ROOTS = List.of("sessions/");
    private static final List<String> DRAFT_ROOTS = List.of("skill_drafts/");

    /**
     * 标准化路径：反斜杠替换、去前导斜杠、拒绝 ".." 遍历
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "路径不能为空");
        }
        String normalized = path.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // 按 / 分段检查，拒绝包含 .. 的段
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "路径遍历不允许: " + path);
            }
        }
        return normalized;
    }

    /**
     * 校验路径可读（skills/ 或 sessions/）
     */
    public static void checkReadable(String path) {
        String p = normalize(path);
        if (READABLE_ROOTS.stream().noneMatch(p::startsWith)) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "读取不允许: " + path);
        }
    }

    /**
     * 校验路径可写（仅 sessions/）
     */
    public static void checkWritable(String path) {
        String p = normalize(path);
        if (WRITABLE_ROOTS.stream().noneMatch(p::startsWith)) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "写入不允许: " + path);
        }
    }

    /**
     * 校验路径在草稿区域（skill_drafts/）
     */
    public static void checkDraftAllowed(String path) {
        String p = normalize(path);
        if (DRAFT_ROOTS.stream().noneMatch(p::startsWith)) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "草稿区域不允许: " + path);
        }
    }
}
