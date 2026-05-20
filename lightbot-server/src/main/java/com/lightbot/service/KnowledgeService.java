package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Knowledge;

import java.util.List;

/**
 * 知识库服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface KnowledgeService extends IService<Knowledge> {

    /**
     * 创建知识库
     *
     * @param knowledge 知识库信息
     * @return 知识库
     */
    Knowledge create(Knowledge knowledge);

    /**
     * 更新知识库
     *
     * @param knowledge 知识库信息
     * @return 知识库
     */
    Knowledge update(Knowledge knowledge);

    /**
     * 分页查询当前用户有权限的知识库
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<Knowledge> listMyKnowledge(int pageNum, int pageSize);

    /**
     * 删除知识库（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 获取知识库详情（需要成员权限）
     *
     * @param id 知识库ID
     * @return 知识库
     */
    Knowledge getByIdWithPermission(Long id);

    /**
     * 更新知识库统计信息
     *
     * @param knowledgeId 知识库ID
     * @param docDelta    文档增量
     * @param chunkDelta  分块增量
     * @param tokenDelta  Token增量
     */
    void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta);

    /**
     * AI生成思维导图
     *
     * @param knowledgeId 知识库ID
     * @param providerId  模型提供商ID
     * @return 思维导图JSON对象
     */
    Object generateMindmap(Long knowledgeId, Long providerId);

    /**
     * 获取已有思维导图数据
     *
     * @param knowledgeId 知识库ID
     * @return 思维导图JSON对象，未生成返回null
     */
    Object getMindmap(Long knowledgeId);
}
