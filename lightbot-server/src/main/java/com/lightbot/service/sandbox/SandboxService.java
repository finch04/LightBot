package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;

import java.util.List;
import java.util.Map;

/**
 * 统一沙盒服务：代码执行 + 文件管理
 *
 * @author finch
 * @since 2026-06-24
 */
public interface SandboxService {

    /**
     * 执行代码片段
     *
     * @param code      代码内容
     * @param language  编程语言（java / javascript），null 默认 java
     * @param params    传入参数（代码中可通过 params 访问）
     * @param timeoutMs 超时时间（毫秒），null 使用默认 5000
     * @return 执行结果
     */
    CodeExecResult executeCode(String code, String language, Map<String, Object> params, Long timeoutMs);

    /**
     * 读取沙盒文件
     *
     * @param path MinIO 路径（如 skills/xxx/SKILL.md）
     * @return 文件内容
     */
    String readFile(String path);

    /**
     * 写入沙盒文件（仅限 threads/ 工作区）
     *
     * @param path    MinIO 路径
     * @param content 文件内容
     */
    void writeFile(String path, String content);

    /**
     * 列出目录文件
     *
     * @param dirPath 目录路径
     * @return 文件路径列表
     */
    List<String> listFiles(String dirPath);

    /**
     * 删除沙盒文件（仅限 threads/ 工作区）
     *
     * @param path MinIO 路径
     */
    void deleteFile(String path);

    /**
     * 检查文件是否存在
     *
     * @param path MinIO 路径
     * @return 是否存在
     */
    boolean fileExists(String path);
}
