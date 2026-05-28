package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalEvaluatorVersion;

import java.util.List;

/**
 * 评测器版本服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalEvaluatorVersionService extends IService<EvalEvaluatorVersion> {

    /**
     * 创建评测器版本
     *
     * @param evaluatorId 评测器ID
     * @param version     版本号
     * @param prompt      评测提示词
     * @param variables   变量定义（JSON）
     * @param modelConfig 模型配置（JSON）
     * @return 评测器版本实体
     */
    EvalEvaluatorVersion create(Long evaluatorId, String version, String prompt, String variables, String modelConfig);

    /**
     * 查询指定评测器的所有版本
     *
     * @param evaluatorId 评测器ID
     * @return 版本列表
     */
    List<EvalEvaluatorVersion> listByEvaluatorId(Long evaluatorId);

    /**
     * 根据评测器ID和版本号获取版本详情
     *
     * @param evaluatorId 评测器ID
     * @param version     版本号
     * @return 评测器版本实体
     */
    EvalEvaluatorVersion getByEvaluatorIdAndVersion(Long evaluatorId, String version);
}
