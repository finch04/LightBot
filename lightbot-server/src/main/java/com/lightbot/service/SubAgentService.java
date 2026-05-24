package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.SubAgentRequest;
import com.lightbot.entity.SubAgent;

import java.util.List;

/**
 * SubAgent 服务接口
 *
 * @author finch
 * @since 2026-05-24
 */
public interface SubAgentService extends IService<SubAgent> {

    /**
     * 创建 SubAgent
     */
    SubAgent create(SubAgentRequest request);

    /**
     * 更新 SubAgent
     */
    SubAgent update(SubAgentRequest request);

    /**
     * 分页查询 SubAgent
     */
    Page<SubAgent> listPage(int pageNum, int pageSize, String keyword, Boolean isBuiltin);

    /**
     * 根据 name 获取 SubAgent
     */
    SubAgent getByName(String name);

    /**
     * 获取所有启用的 SubAgent
     */
    List<SubAgent> listEnabled();

    /**
     * 删除 SubAgent（内置不可删）
     */
    void deleteById(Long id);

    /**
     * 设置启用状态
     */
    void setEnabled(Long id, boolean enabled);
}