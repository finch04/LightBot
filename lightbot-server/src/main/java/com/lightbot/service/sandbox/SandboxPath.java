package com.lightbot.service.sandbox;

import com.lightbot.util.SessionStoragePath;

/**
 * 沙盒路径：统一路径表示 + 类型安全
 *
 * @param type         路径类型
 * @param relativePath 相对路径（不含类型前缀）
 * @author finch
 * @since 2026-06-24
 */
public record SandboxPath(PathType type, String relativePath) {

    public enum PathType {
        /** skills/{slug}/xxx（只读） */
        SKILL,
        /** sessions/{sessionId}/workspace/xxx（读写） */
        WORKSPACE,
        /** sessions/{sessionId}/outputs/xxx（读写，AI 交付物） */
        OUTPUT
    }

    /**
     * 构建 Skill 路径
     *
     * @param slug         Skill 标识
     * @param relativePath 相对路径（如 SKILL.md、scripts/xxx.js）
     */
    public static SandboxPath skill(String slug, String relativePath) {
        return new SandboxPath(PathType.SKILL, slug + "/" + relativePath);
    }

    /**
     * 构建工作区路径（MinIO 落在 sessions/{sessionId}/workspace/{relativePath}）。
     *
     * @param sessionId    会话 ID
     * @param relativePath 相对路径（如 output.txt、data/result.json）
     */
    public static SandboxPath workspace(String sessionId, String relativePath) {
        return new SandboxPath(PathType.WORKSPACE, sessionId + "/" + SessionStoragePath.WORKSPACE_DIR + "/" + relativePath);
    }

    /**
     * 构建 AI 产出路径（MinIO 落在 sessions/{sessionId}/outputs/{relativePath}）。
     *
     * @param sessionId    会话 ID
     * @param relativePath 相对 outputs/ 的路径（如 files/report.pdf）
     */
    public static SandboxPath output(String sessionId, String relativePath) {
        return new SandboxPath(PathType.OUTPUT, sessionId + "/" + SessionStoragePath.OUTPUTS_DIR + "/" + relativePath);
    }

    /**
     * 转换为 MinIO 完整路径
     */
    public String toMinioPath() {
        return switch (type) {
            case SKILL -> "skills/" + relativePath;
            case WORKSPACE, OUTPUT -> "sessions/" + relativePath;
        };
    }
}
