package com.lightbot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 * <p>所有业务错误信息统一管理，禁止在代码中硬编码错误字符串</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 通用 ==========
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== 用户模块 ==========
    USER_NOT_FOUND(401, "用户不存在"),
    USERNAME_EXISTS(400, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(400, "用户名或密码错误"),
    ACCOUNT_DISABLED(403, "账号已被禁用"),

    // ========== 会话模块 ==========
    SESSION_NOT_FOUND(400, "会话不存在"),

    // ========== 模型提供商模块 ==========
    MODEL_PROVIDER_NOT_FOUND(400, "模型提供商不存在"),

    // ========== 知识库模块 ==========
    KNOWLEDGE_NOT_FOUND(400, "知识库不存在"),
    KNOWLEDGE_NO_PERMISSION(403, "无权访问该知识库"),
    KNOWLEDGE_ROLE_INSUFFICIENT(403, "权限不足，需要%s及以上权限"),
    KNOWLEDGE_MEMBER_EXISTS(400, "该用户已是知识库成员"),
    KNOWLEDGE_MEMBER_NOT_FOUND(400, "该用户不是知识库成员"),
    KNOWLEDGE_CREATOR_ROLE_IMMUTABLE(400, "不能修改创建者角色"),
    KNOWLEDGE_CREATOR_CANNOT_REMOVE(400, "不能移除创建者"),

    // ========== 文档模块 ==========
    DOCUMENT_UNSUPPORTED_TYPE(400, "目前仅支持 Markdown 文件"),
    DOCUMENT_ALREADY_EXISTS(400, "该文件已上传过"),
    DOCUMENT_NOT_FOUND(400, "文档不存在"),
    DOCUMENT_READ_FAILED(500, "读取文档内容失败"),

    // ========== RAG 模块 ==========
    RAG_KNOWLEDGE_NOT_FOUND(400, "知识库不存在"),

    // ========== 文件存储 ==========
    FILE_UPLOAD_FAILED(500, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(500, "文件下载失败"),
    FILE_URL_FAILED(500, "获取文件URL失败");

    /** HTTP状态码 */
    private final int code;

    /** 错误信息 */
    private final String message;
}
