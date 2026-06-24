package com.lightbot.service;

import com.lightbot.dto.DocumentVersionVO;

import java.util.List;

/**
 * 文档版本服务接口
 *
 * @author finch
 * @since 2026-06-17
 */
public interface DocumentVersionService {

    /**
     * 保存当前版本快照（覆盖前调用）
     *
     * @param documentId  文档ID
     * @param version     当前版本号
     * @param contentHash 当前内容哈希
     * @param content     当前内容
     */
    void saveVersion(Long documentId, Integer version, String contentHash, String content);

    /**
     * 查询文档的版本列表（按版本号倒序）
     *
     * @param documentId 文档ID
     * @return 版本列表
     */
    List<DocumentVersionVO> listVersions(Long documentId);

    /**
     * 获取指定版本的内容
     *
     * @param versionId 版本记录ID
     * @return 版本内容
     */
    String getVersionContent(Long versionId);

    /**
     * 回滚到指定版本
     *
     * @param documentId 文档ID
     * @param versionId  版本记录ID
     */
    void rollback(Long documentId, Long versionId);

    /**
     * 删除文档的所有版本记录及 MinIO 文件
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
