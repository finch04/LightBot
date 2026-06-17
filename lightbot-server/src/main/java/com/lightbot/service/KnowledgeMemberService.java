package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.KnowledgeMemberVO;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.KnowledgeRole;

import java.util.List;

/**
 * 知识库成员服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface KnowledgeMemberService extends IService<KnowledgeMember> {

    /**
     * 查询用户加入的所有知识库ID
     *
     * @param userId 用户ID
     * @return 知识库ID列表
     */
    List<Long> listKnowledgeIdsByUserId(Long userId);

    /**
     * 获取用户在指定知识库中的角色
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @return 角色，不存在返回null
     */
    KnowledgeRole getMemberRole(Long knowledgeId, Long userId);

    /**
     * 校验用户是否有至少指定等级的角色
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @param requiredRole 最低要求角色
     * @return 是否有权限
     */
    boolean hasPermission(Long knowledgeId, Long userId, KnowledgeRole requiredRole);

    /**
     * 添加成员到知识库
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @param role        角色
     */
    void addMember(Long knowledgeId, Long userId, KnowledgeRole role);

    /**
     * 更新成员角色
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @param role        新角色
     */
    void updateMemberRole(Long knowledgeId, Long userId, KnowledgeRole role);

    /**
     * 移除成员
     *
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     */
    void removeMember(Long knowledgeId, Long userId);

    /**
     * 删除知识库的所有成员关系
     *
     * @param knowledgeId 知识库ID
     */
    void removeByKnowledgeId(Long knowledgeId);

    /**
     * 校验当前用户是否为知识库成员（任意角色）
     *
     * @param knowledgeId 知识库ID
     */
    void checkMember(Long knowledgeId);

    /**
     * 校验当前用户是否具有指定等级的角色
     *
     * @param knowledgeId  知识库ID
     * @param requiredRole 最低要求角色
     */
    void checkPermission(Long knowledgeId, KnowledgeRole requiredRole);

    /**
     * 查询知识库的所有成员
     *
     * @param knowledgeId 知识库ID
     * @return 成员列表
     */
    List<KnowledgeMember> listMembers(Long knowledgeId);

    /**
     * 查询知识库的所有成员（含用户昵称、头像）
     *
     * @param knowledgeId 知识库ID
     * @return 成员VO列表
     */
    List<KnowledgeMemberVO> listMemberVOs(Long knowledgeId);
}
