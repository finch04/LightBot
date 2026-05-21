package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.SkillMapper;
import com.lightbot.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Skill 服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill>
        implements SkillService {

    @Override
    public Skill create(SkillRequest request) {
        Skill skill = new Skill();
        skill.setAgentId(request.getAgentId());
        skill.setToolId(request.getToolId());
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setConfig(request.getConfig());
        skill.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        skill.setStatus(CommonStatus.ACTIVE);
        save(skill);
        return skill;
    }

    @Override
    public Skill update(SkillRequest request) {
        Skill skill = getById(request.getId());
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        skill.setToolId(request.getToolId());
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setConfig(request.getConfig());
        skill.setSortOrder(request.getSortOrder());
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
        if (!removeById(id)) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
    }
}
