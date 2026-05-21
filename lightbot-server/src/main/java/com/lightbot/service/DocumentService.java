package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface DocumentService extends IService<Document> {

    /**
     * 上传文档（仅存储，不分块不向量化）
     *
     * @param knowledgeId 知识库ID
     * @param file        上传的文件
     * @return 文档记录
     */
    Document uploadDocument(Long knowledgeId, MultipartFile file);

    /**
     * 批量上传文档
     *
     * @param knowledgeId 知识库ID
     * @param files       上传的文件列表
     * @return 文档记录列表
     */
    List<Document> uploadDocuments(Long knowledgeId, List<MultipartFile> files);

    /**
     * 入库：分块 + 向量化
     *
     * @param documentId   文档ID
     * @param embeddingJson 入库配置JSON（chunkStrategy/chunkSize/chunkOverlap/chunkDelimiter）
     */
    void ingestDocument(Long documentId, String embeddingJson);

    /**
     * 预览分块结果（不入库）
     *
     * @param documentId   文档ID
     * @param embeddingJson 入库配置JSON
     * @return 分块文本列表
     */
    List<String> previewChunks(Long documentId, String embeddingJson);

    /**
     * 获取文档预览内容
     *
     * @param documentId 文档ID
     * @return 文档内容
     */
    String previewDocument(Long documentId);

    /**
     * 查询知识库下的文档列表
     *
     * @param knowledgeId 知识库ID
     * @return 文档列表
     */
    List<Document> listByKnowledgeId(Long knowledgeId);

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     */
    void deleteDocument(Long documentId);

    /**
     * 获取文档下载信息（预签名URL + 文件类型）
     *
     * @param documentId 文档ID
     * @return 下载信息
     */
    DocumentDownloadVO getDocumentDownloadUrl(Long documentId);
}
