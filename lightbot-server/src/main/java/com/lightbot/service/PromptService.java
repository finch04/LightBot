package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Prompt;

/**
 * 提示词服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface PromptService extends IService<Prompt> {

    /**
     * 创建提示词
     *
     * @param promptKey   提示词唯一标识
     * @param description 描述
     * @param tags        标签
     * @param userId      创建者ID
     * @return 提示词实体
     */
    Prompt create(String promptKey, String description, String tags, Long userId);

    /**
     * 更新提示词
     *
     * @param id          主键ID
     * @param description 描述
     * @param tags        标签
     */
    void update(Long id, String description, String tags);

    /**
     * 删除提示词（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 分页查询提示词列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  搜索关键词
     * @param userId   用户ID
     * @return 分页结果
     */
    Page<Prompt> list(int pageNum, int pageSize, String keyword, Long userId);
}
