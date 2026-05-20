package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.ChunkVO;
import com.lightbot.entity.Chunk;
import com.lightbot.enums.ChunkStatus;

import java.util.List;

/**
 * 文档分块服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface ChunkService extends IService<Chunk> {

    /**
     * Markdown 分块：优先按标题拆分，超长段落再按 token 数切分
     *
     * @param content      Markdown 内容
     * @param chunkSize    分块大小（字符数）
     * @param chunkOverlap 分块重叠（字符数）
     * @return 分块列表
     */
    List<String> splitMarkdown(String content, int chunkSize, int chunkOverlap);

    /**
     * 保存分块到数据库
     *
     * @param documentId  文档ID
     * @param knowledgeId 知识库ID
     * @param index       分块序号
     * @param content     分块内容
     */
    void saveChunk(Long documentId, Long knowledgeId, int index, String content);

    /**
     * 保存分块到数据库（含向量化状态）
     *
     * @param documentId  文档ID
     * @param knowledgeId 知识库ID
     * @param index       分块序号
     * @param content     分块内容
     * @param status      向量化状态
     */
    void saveChunk(Long documentId, Long knowledgeId, int index, String content, ChunkStatus status);

    /**
     * 查询文档的分块列表
     *
     * @param documentId 文档ID
     * @return 分块列表
     */
    List<Chunk> listByDocumentId(Long documentId);

    /**
     * 查询文档的分块列表（含向量化状态）
     *
     * @param documentId 文档ID
     * @return 分块VO列表
     */
    List<ChunkVO> listChunkVOsByDocumentId(Long documentId);
}
