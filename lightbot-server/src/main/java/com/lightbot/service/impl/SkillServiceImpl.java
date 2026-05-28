package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.SkillMapper;
import com.lightbot.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Skill 服务实现类
 * <p>支持「全局可复用 Skill」与「旧的按 Agent 私有 Skill」两种模式，
 * 新建 Skill 默认 scope=global，必须填写 slug。</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill>
        implements SkillService {

    /** Skill slug 规范：3~64 个字符的英文/数字/短横线 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Skill create(SkillRequest request) {
        // 1. 解析作用域：默认 global
        String scope = StringUtils.hasText(request.getScope()) ? request.getScope() : "global";
        if ("global".equals(scope)) {
            validateSlug(request.getSlug(), null);
        }

        // 2. 组装实体
        Skill skill = new Skill();
        skill.setSlug(request.getSlug());
        skill.setAgentId("agent".equals(scope) ? request.getAgentId() : null);
        skill.setName(request.getName());
        skill.setDisplayName(request.getDisplayName());
        skill.setDescription(request.getDescription());
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setConfig(request.getConfig());
        skill.setToolIds(toJsonArray(request.getToolIds()));
        skill.setMcpServerIds(toJsonArray(request.getMcpServerIds()));
        skill.setModelId(request.getModelId());
        skill.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        skill.setStatus(CommonStatus.ACTIVE);
        skill.setScope(scope);
        skill.setIsBuiltin(0);
        save(skill);
        return skill;
    }

    @Override
    public Skill update(SkillRequest request) {
        Skill skill = getById(request.getId());
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(skill.getIsBuiltin())) {
            throw new BizException("内置 Skill 不可编辑");
        }
        if ("global".equals(skill.getScope()) && StringUtils.hasText(request.getSlug())
                && !request.getSlug().equals(skill.getSlug())) {
            validateSlug(request.getSlug(), skill.getId());
            skill.setSlug(request.getSlug());
        }
        skill.setName(request.getName());
        skill.setDisplayName(request.getDisplayName());
        skill.setDescription(request.getDescription());
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setConfig(request.getConfig());
        skill.setToolIds(toJsonArray(request.getToolIds()));
        skill.setMcpServerIds(toJsonArray(request.getMcpServerIds()));
        skill.setModelId(request.getModelId());
        skill.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        updateById(skill);
        return skill;
    }

    @Override
    public List<Skill> listByAgentId(Long agentId, String name) {
        return list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getAgentId, agentId)
                .like(StringUtils.hasText(name), Skill::getName, name)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(skill.getIsBuiltin())) {
            throw new BizException("内置 Skill 不可删除");
        }
        if (!removeById(id)) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
    }

    @Override
    public Page<Skill> listGlobal(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                .eq(Skill::getScope, "global")
                .orderByDesc(Skill::getIsBuiltin)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Skill::getName, keyword)
                    .or().like(Skill::getDisplayName, keyword)
                    .or().like(Skill::getSlug, keyword));
        }
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<Skill> listEnabled() {
        return list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getScope, "global")
                .eq(Skill::getStatus, CommonStatus.ACTIVE)
                .orderByDesc(Skill::getIsBuiltin)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime));
    }

    @Override
    public Skill getBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Skill>().eq(Skill::getSlug, slug), false);
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        skill.setStatus(enabled ? CommonStatus.ACTIVE : CommonStatus.DISABLED);
        updateById(skill);
    }

    /** 校验 slug 格式与唯一性 */
    private void validateSlug(String slug, Long currentId) {
        if (!StringUtils.hasText(slug)) {
            throw new BizException("全局 Skill 必须填写 slug");
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new BizException("slug 只能包含小写字母、数字和短横线");
        }
        Skill exists = getBySlug(slug);
        if (exists != null && !exists.getId().equals(currentId)) {
            throw new BizException("slug 已存在: " + slug);
        }
    }

    /** 将字符串 ID 列表序列化为 JSON 数组（无元素时返回 "[]"） */
    private String toJsonArray(List<String> items) {
        try {
            if (items == null || items.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
