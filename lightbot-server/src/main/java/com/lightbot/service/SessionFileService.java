package com.lightbot.service;

import com.lightbot.dto.SessionFileContentVO;
import com.lightbot.dto.SessionFileTreeResponseVO;

/**
 * 会话文件服务：基于 MinIO 扫描的懒加载目录树 / 内容预览 / 下载 / 删除。
 * <p>参考 Yuxi viewer_filesystem_service。</p>
 *
 * @author finch
 * @since 2026-06-30
 */
public interface SessionFileService {

    /**
     * 列出会话指定路径下的直接子条目（懒加载单层）+ 全局统计。
     *
     * @param sessionId 会话 ID
     * @param path      相对会话根路径，空或 {@code /} 表示根
     * @return 树响应
     */
    SessionFileTreeResponseVO listDirectory(Long sessionId, String path);

    /**
     * 读取文件内容或返回预签名预览 URL。
     *
     * @param sessionId 会话 ID
     * @param path      相对会话根路径
     * @return 内容响应
     */
    SessionFileContentVO readContent(Long sessionId, String path);

    /**
     * 生成下载预签名 URL。
     *
     * @param sessionId 会话 ID
     * @param path      相对会话根路径
     * @return 预签名 URL
     */
    String getDownloadUrl(Long sessionId, String path);

    /**
     * 删除会话文件（MinIO 对象 + 同步 attachments 索引）。
     *
     * @param sessionId 会话 ID
     * @param path      相对会话根路径
     */
    void deleteFile(Long sessionId, String path);
}
