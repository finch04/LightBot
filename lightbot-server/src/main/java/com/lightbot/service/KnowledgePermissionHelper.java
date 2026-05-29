package com.lightbot.service;

import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识库权限校验工具，供各子功能 Service 复用
 *
 * @author finch
 * @since 2026-05-29
 */
@Component
@RequiredArgsConstructor
public class KnowledgePermissionHelper {

    private final KnowledgeMemberService knowledgeMemberService;

    /**
     * 校验当前用户是否为知识库成员（任意角色）
     *
     * @param knowledgeId 知识库ID
     */
    public void checkMember(Long knowledgeId) {
        long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole role = knowledgeMemberService.getMemberRole(knowledgeId, userId);
        if (role == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_PERMISSION);
        }
    }

    /**
     * 校验当前用户是否具有指定等级的角色
     *
     * @param knowledgeId  知识库ID
     * @param requiredRole 最低要求角色
     */
    public void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeMemberService.hasPermission(knowledgeId, userId, requiredRole)) {
            throw new BizException(ErrorCode.KNOWLEDGE_ROLE_INSUFFICIENT, requiredRole.getDesc());
        }
    }
}
