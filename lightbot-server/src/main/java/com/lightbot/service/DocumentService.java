package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.DocumentDownloadVO;
import com.lightbot.dto.UrlFetchPreviewVO;
import com.lightbot.dto.UrlSaveRequest;
import com.lightbot.entity.Document;
import com.lightbot.entity.Task;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.BiConsumer;

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
     * @param ocrEnabled  是否启用OCR识别
     * @return 文档记录
     */
    Document uploadDocument(Long knowledgeId, MultipartFile file, boolean ocrEnabled);

    /**
     * 批量上传文档
     *
     * @param knowledgeId 知识库ID
     * @param files       上传的文件列表
     * @param ocrEnabled  是否启用OCR识别
     * @return 文档记录列表
     */
    List<Document> uploadDocuments(Long knowledgeId, List<MultipartFile> files, boolean ocrEnabled);

    /**
     * 入库：创建任务并推入队列
     *
     * @param documentId   文档ID
     * @param embeddingJson 入库配置JSON（chunkStrategy/chunkSize/chunkOverlap/chunkDelimiter）
     * @return 创建的任务
     */
    Task ingestDocument(Long documentId, String embeddingJson);

    /**
     * 同步执行文档入库（分块 + 向量化），由任务消费者调用
     *
     * @param documentId    文档ID
     * @param embeddingJson 入库配置JSON
     * @param progressCallback 进度回调 (progress 0-100, message)
     */
    void processDocumentWithProgress(Long documentId, String embeddingJson, BiConsumer<Integer, String> progressCallback);

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
     * 分页查询知识库下的文档列表（支持名称搜索）
     *
     * @param knowledgeId 知识库ID
     * @param keyword     搜索关键词（按文档名称模糊匹配）
     * @param pageNum     页码
     * @param pageSize    每页数量
     * @return 分页文档列表
     */
    Page<Document> listByKnowledgeIdWithPage(Long knowledgeId, String keyword, int pageNum, int pageSize);

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

    /**
     * 预览 URL 网页内容（不入库）
     */
    UrlFetchPreviewVO previewUrlDocument(Long knowledgeId, String url);

    /**
     * 保存已预览的 URL 网页内容为文档
     */
    Document saveUrlDocument(Long knowledgeId, UrlSaveRequest request);

    /**
     * 从 URL 抓取内容并创建文档记录（抓取后立即入库，兼容旧调用）
     */
    Document fetchUrlDocument(Long knowledgeId, String url);
}
