package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalEvaluator;

/**
 * 评测器服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalEvaluatorService extends IService<EvalEvaluator> {

    /**
     * 创建评测器
     *
     * @param name        评测器名称
     * @param description 描述
     * @param userId      创建者ID
     * @return 评测器实体
     */
    EvalEvaluator create(String name, String description, Long userId);

    /**
     * 更新评测器
     *
     * @param id          主键ID
     * @param name        评测器名称
     * @param description 描述
     */
    void update(Long id, String name, String description);

    /**
     * 删除评测器（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 分页查询评测器列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  搜索关键词
     * @param userId   用户ID
     * @return 分页结果
     */
    Page<EvalEvaluator> list(int pageNum, int pageSize, String keyword, Long userId);
}
