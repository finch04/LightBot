package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.KnowledgeMapper;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge>
        implements KnowledgeService {

    private final KnowledgeMemberService knowledgeMemberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Knowledge create(Knowledge knowledge) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 初始化知识库字段
        knowledge.setUserId(userId);
        knowledge.setStatus(CommonStatus.ACTIVE);
        knowledge.setDocumentCount(0);
        knowledge.setChunkCount(0);
        knowledge.setTotalTokens(0L);
        save(knowledge);

        // 2. 创建者自动成为成员（CREATOR角色）
        KnowledgeMember member = new KnowledgeMember();
        member.setKnowledgeId(knowledge.getId());
        member.setUserId(userId);
        member.setRole(KnowledgeRole.CREATOR);
        knowledgeMemberService.save(member);

        return knowledge;
    }

    @Override
    public Knowledge update(Knowledge knowledge) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledge.getId(), KnowledgeRole.MANAGER);

        // 2. 校验存在性
        Knowledge existing = getById(knowledge.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 更新允许修改的字段
        existing.setName(knowledge.getName());
        existing.setDescription(knowledge.getDescription());
        existing.setEmbeddingModel(knowledge.getEmbeddingModel());
        existing.setChunkSize(knowledge.getChunkSize());
        existing.setChunkOverlap(knowledge.getChunkOverlap());
        existing.setConfig(knowledge.getConfig());
        updateById(existing);
        return existing;
    }

    @Override
    public Page<Knowledge> listMyKnowledge(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 查询用户加入的所有知识库ID
        List<Long> knowledgeIds = knowledgeMemberService.listKnowledgeIdsByUserId(userId);
        if (knowledgeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 2. 分页查询这些知识库
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Knowledge>()
                        .in(Knowledge::getId, knowledgeIds)
                        .eq(Knowledge::getStatus, CommonStatus.ACTIVE)
                        .orderByDesc(Knowledge::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        // 1. 权限校验：仅CREATOR可删除
        checkPermission(id, KnowledgeRole.CREATOR);

        // 2. 校验存在性
        Knowledge knowledge = getById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 逻辑删除知识库
        removeById(id);

        // 4. 同时删除所有成员关系
        knowledgeMemberService.removeByKnowledgeId(id);
    }

    @Override
    public Knowledge getByIdWithPermission(Long id) {
        // 1. 权限校验：需要成员权限
        checkMember(id);

        // 2. 返回知识库详情
        return getById(id);
    }

    @Override
    public void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta) {
        // 增量更新知识库统计数据
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        knowledge.setDocumentCount(knowledge.getDocumentCount() + docDelta);
        knowledge.setChunkCount(knowledge.getChunkCount() + chunkDelta);
        knowledge.setTotalTokens(knowledge.getTotalTokens() + tokenDelta);
        updateById(knowledge);
    }

    // ========== 权限校验 ==========

    /**
     * 校验当前用户是否为知识库成员
     */
    private void checkMember(Long knowledgeId) {
        long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole role = knowledgeMemberService.getMemberRole(knowledgeId, userId);
        if (role == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_PERMISSION);
        }
    }

    /**
     * 校验当前用户是否具有指定等级的角色
     */
    private void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeMemberService.hasPermission(knowledgeId, userId, requiredRole)) {
            throw new BizException(ErrorCode.KNOWLEDGE_ROLE_INSUFFICIENT, requiredRole.getDesc());
        }
    }
}
