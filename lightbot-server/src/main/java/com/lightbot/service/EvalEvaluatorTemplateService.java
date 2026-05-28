package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalEvaluatorTemplate;

import java.util.List;

/**
 * 评测器模板服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalEvaluatorTemplateService extends IService<EvalEvaluatorTemplate> {

    /**
     * 查询所有评测器模板
     *
     * @return 模板列表
     */
    List<EvalEvaluatorTemplate> listAll();

    /**
     * 根据模板标识获取模板详情
     *
     * @param evaluatorTemplateKey 模板唯一标识
     * @return 模板实体
     */
    EvalEvaluatorTemplate getByKey(String evaluatorTemplateKey);
}
