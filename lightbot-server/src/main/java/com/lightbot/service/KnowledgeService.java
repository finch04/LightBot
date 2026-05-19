package com.lightbot.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.CommonStatus;
import com.lightbot.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 知识库服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService extends ServiceImpl<KnowledgeMapper, Knowledge> {

    /**
     * 创建知识库
     */
    public Knowledge create(Knowledge knowledge) {
        long userId = StpUtil.getLoginIdAsLong();
        knowledge.setUserId(userId);
        knowledge.setStatus(CommonStatus.ACTIVE);
        knowledge.setDocumentCount(0);
        knowledge.setChunkCount(0);
        knowledge.setTotalTokens(0L);
        save(knowledge);
        return knowledge;
    }

    /**
     * 更新知识库
     */
    public Knowledge update(Knowledge knowledge) {
        Knowledge existing = getById(knowledge.getId());
        if (existing == null) {
            throw new BizException("知识库不存在");
        }
        existing.setName(knowledge.getName());
        existing.setDescription(knowledge.getDescription());
        existing.setEmbeddingModel(knowledge.getEmbeddingModel());
        existing.setChunkSize(knowledge.getChunkSize());
        existing.setChunkOverlap(knowledge.getChunkOverlap());
        existing.setConfig(knowledge.getConfig());
        updateById(existing);
        return existing;
    }

    /**
     * 分页查询当前用户的知识库
     */
    public Page<Knowledge> listMyKnowledge(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Knowledge>()
                        .eq(Knowledge::getUserId, userId)
                        .eq(Knowledge::getStatus, CommonStatus.ACTIVE)
                        .orderByDesc(Knowledge::getCreateTime));
    }

    /**
     * 删除知识库（逻辑删除）
     */
    public void deleteById(Long id) {
        Knowledge knowledge = getById(id);
        if (knowledge == null) {
            throw new BizException("知识库不存在");
        }
        removeById(id);
    }

    /**
     * 更新知识库统计信息
     */
    public void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        knowledge.setDocumentCount(knowledge.getDocumentCount() + docDelta);
        knowledge.setChunkCount(knowledge.getChunkCount() + chunkDelta);
        knowledge.setTotalTokens(knowledge.getTotalTokens() + tokenDelta);
        updateById(knowledge);
    }
}
