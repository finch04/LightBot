package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.EvalExperimentOverviewVO;
import com.lightbot.entity.EvalExperimentResult;

import java.util.List;

/**
 * 评测实验结果服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalExperimentResultService extends IService<EvalExperimentResult> {

    /**
     * 获取实验概览统计
     *
     * @param experimentId 实验ID
     * @return 概览数据列表
     */
    List<EvalExperimentOverviewVO> getOverview(Long experimentId);

    /**
     * 分页查询实验结果列表
     *
     * @param experimentId       实验ID
     * @param evaluatorVersionId 评测器版本ID（可选，为null时查全部）
     * @param pageNum            页码
     * @param pageSize           每页数量
     * @return 分页结果
     */
    Page<EvalExperimentResult> listByExperiment(Long experimentId, Long evaluatorVersionId, int pageNum, int pageSize);

    /**
     * 删除指定实验的所有结果（级联删除，跳过权限校验）
     *
     * @param experimentId 实验ID
     */
    void removeByExperimentId(Long experimentId);
}
