package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.KnowledgeMemberMapper;
import com.lightbot.service.KnowledgeMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库成员服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class KnowledgeMemberServiceImpl extends ServiceImpl<KnowledgeMemberMapper, KnowledgeMember>
        implements KnowledgeMemberService {

    @Override
    public List<Long> listKnowledgeIdsByUserId(Long userId) {
        return list(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getUserId, userId))
                .stream()
                .map(KnowledgeMember::getKnowledgeId)
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgeRole getMemberRole(Long knowledgeId, Long userId) {
        KnowledgeMember member = getOne(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .eq(KnowledgeMember::getUserId, userId));
        return member != null ? member.getRole() : null;
    }

    @Override
    public boolean hasPermission(Long knowledgeId, Long userId, KnowledgeRole requiredRole) {
        KnowledgeRole role = getMemberRole(knowledgeId, userId);
        if (role == null) {
            return false;
        }
        // 角色等级：CREATOR(0) > MANAGER(1) > DEVELOPER(2) > VIEWER(3)
        return role.isAtLeast(requiredRole);
    }

    @Override
    public void addMember(Long knowledgeId, Long userId, KnowledgeRole role) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.MANAGER);

        // 2. 检查是否已是成员
        KnowledgeMember existing = getOne(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .eq(KnowledgeMember::getUserId, userId));
        if (existing != null) {
            throw new BizException(ErrorCode.KNOWLEDGE_MEMBER_EXISTS);
        }

        // 3. 添加成员
        KnowledgeMember member = new KnowledgeMember();
        member.setKnowledgeId(knowledgeId);
        member.setUserId(userId);
        member.setRole(role);
        save(member);
    }

    @Override
    public void updateMemberRole(Long knowledgeId, Long userId, KnowledgeRole role) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.MANAGER);

        // 2. 查找成员
        KnowledgeMember member = getOne(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .eq(KnowledgeMember::getUserId, userId));
        if (member == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_MEMBER_NOT_FOUND);
        }

        // 3. 不允许修改创建者角色
        if (member.getRole() == KnowledgeRole.CREATOR) {
            throw new BizException(ErrorCode.KNOWLEDGE_CREATOR_ROLE_IMMUTABLE);
        }

        // 4. 更新角色
        member.setRole(role);
        updateById(member);
    }

    @Override
    public void removeMember(Long knowledgeId, Long userId) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.MANAGER);

        // 2. 查找成员
        KnowledgeMember member = getOne(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .eq(KnowledgeMember::getUserId, userId));
        if (member == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_MEMBER_NOT_FOUND);
        }

        // 3. 不允许移除创建者
        if (member.getRole() == KnowledgeRole.CREATOR) {
            throw new BizException(ErrorCode.KNOWLEDGE_CREATOR_CANNOT_REMOVE);
        }

        // 4. 移除成员
        removeById(member.getId());
    }

    @Override
    public void removeByKnowledgeId(Long knowledgeId) {
        remove(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId));
    }

    @Override
    public List<KnowledgeMember> listMembers(Long knowledgeId) {
        // 1. 权限校验：需要成员权限
        checkMember(knowledgeId);

        // 2. 查询成员列表
        return list(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId));
    }

    // ========== 权限校验 ==========

    /**
     * 校验当前用户是否为知识库成员
     */
    private void checkMember(Long knowledgeId) {
        long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole role = getMemberRole(knowledgeId, userId);
        if (role == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_PERMISSION);
        }
    }

    /**
     * 校验当前用户是否具有指定等级的角色
     */
    private void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        long userId = StpUtil.getLoginIdAsLong();
        if (!hasPermission(knowledgeId, userId, requiredRole)) {
            throw new BizException(ErrorCode.KNOWLEDGE_ROLE_INSUFFICIENT, requiredRole.getDesc());
        }
    }
}
