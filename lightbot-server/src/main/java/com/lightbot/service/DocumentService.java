package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
     * 上传文档并处理
     *
     * @param knowledgeId   知识库ID
     * @param file          上传的文件
     * @param chunkStrategy 分块策略（general/book/separator）
     * @return 文档记录
     */
    Document uploadDocument(Long knowledgeId, MultipartFile file, String chunkStrategy);

    /**
     * 批量上传文档
     *
     * @param knowledgeId   知识库ID
     * @param files         上传的文件列表
     * @param chunkStrategy 分块策略（general/book/separator）
     * @return 文档记录列表
     */
    List<Document> uploadDocuments(Long knowledgeId, List<MultipartFile> files, String chunkStrategy);

    /**
     * 异步处理文档：读取内容 -> 分块 -> 存储
     *
     * @param documentId 文档ID
     */
    void processDocumentAsync(Long documentId);

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
}
