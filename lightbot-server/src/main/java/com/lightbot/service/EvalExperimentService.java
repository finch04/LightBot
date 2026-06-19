package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.EvalExperimentCreateRequest;
import com.lightbot.entity.EvalExperiment;
import com.lightbot.entity.Task;

/**
 * 评测实验服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalExperimentService extends IService<EvalExperiment> {

    /**
     * 创建评测实验
     *
     * @param name                  实验名称
     * @param description           描述
     * @param datasetId             数据集ID
     * @param datasetVersionId      数据集版本ID
     * @param datasetVersion        数据集版本号
     * @param evaluationObjectConfig 评测对象配置（JSON）
     * @param evaluatorConfig       评测器配置（JSON）
     * @param userId                创建者ID
     * @return 实验实体
     */
    EvalExperiment create(String name, String description, Long datasetId, Long datasetVersionId, String datasetVersion, String evaluationObjectConfig, String evaluatorConfig, Long userId);

    /**
     * 停止评测实验
     *
     * @param id     实验ID
     * @param userId 操作者ID
     */
    void stop(Long id, Long userId);

    /**
     * 删除评测实验（逻辑删除）
     *
     * @param id     实验ID
     * @param userId 操作者ID
     */
    void deleteById(Long id, Long userId);

    /**
     * 更新实验配置（仅非运行中的实验可修改）
     *
     * @param id      实验ID
     * @param request 更新请求
     * @return 更新后的实验实体
     */
    EvalExperiment update(Long id, EvalExperimentCreateRequest request);

    /**
     * 重新运行评测实验
     *
     * @param id     实验ID
     * @param userId 操作者ID
     * @return 新的实验实体
     */
    EvalExperiment restart(Long id, Long userId);

    /**
     * 分页查询评测实验列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  搜索关键词
     * @param status   实验状态
     * @param userId   用户ID
     * @return 分页结果
     */
    Page<EvalExperiment> list(int pageNum, int pageSize, String keyword, String status, Long userId);

    /**
     * 获取实验详情
     *
     * @param id 实验ID
     * @return 实验实体
     */
    EvalExperiment getDetail(Long id);

    /**
     * 执行评测实验
     *
     * @param experimentId 实验ID
     * @param task         关联任务
     */
    void executeExperiment(Long experimentId, Task task);
}
