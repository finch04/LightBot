package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;

import java.util.List;

/**
 * Skill 服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface SkillService extends IService<Skill> {

    Skill create(SkillRequest request);

    Skill update(SkillRequest request);

    List<Skill> listByAgentId(Long agentId, String name);

    void deleteById(Long id);
}
