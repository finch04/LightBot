package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.dto.EvalExperimentOverviewVO;
import com.lightbot.entity.EvalEvaluator;
import com.lightbot.entity.EvalEvaluatorVersion;
import com.lightbot.entity.EvalExperimentResult;
import com.lightbot.mapper.EvalExperimentResultMapper;
import com.lightbot.service.EvalEvaluatorService;
import com.lightbot.service.EvalEvaluatorVersionService;
import com.lightbot.service.EvalExperimentResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评测实验结果服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalExperimentResultServiceImpl extends ServiceImpl<EvalExperimentResultMapper, EvalExperimentResult>
        implements EvalExperimentResultService {

    private final EvalEvaluatorVersionService evaluatorVersionService;
    private final EvalEvaluatorService evaluatorService;

    @Override
    public List<EvalExperimentOverviewVO> getOverview(Long experimentId) {
        // 1. 查询所有结果
        List<EvalExperimentResult> results = lambdaQuery()
                .eq(EvalExperimentResult::getExperimentId, experimentId)
                .list();

        // 2. 按评估器版本分组
        Map<Long, List<EvalExperimentResult>> grouped = results.stream()
                .filter(r -> r.getEvaluatorVersionId() != null)
                .collect(Collectors.groupingBy(EvalExperimentResult::getEvaluatorVersionId));

        // 3. 计算每个评估器的平均分
        List<EvalExperimentOverviewVO> overviews = new ArrayList<>();
        for (Map.Entry<Long, List<EvalExperimentResult>> entry : grouped.entrySet()) {
            EvalExperimentOverviewVO vo = new EvalExperimentOverviewVO();
            vo.setEvaluatorVersionId(entry.getKey());
            vo.setEvaluatedCount(entry.getValue().size());

            // 获取评估器名称和版本
            EvalEvaluatorVersion ev = evaluatorVersionService.getById(entry.getKey());
            if (ev != null) {
                EvalEvaluator evaluator = evaluatorService.getById(ev.getEvaluatorId());
                vo.setEvaluatorName(evaluator != null ? evaluator.getName() : "评估器#" + ev.getEvaluatorId());
                vo.setEvaluatorVersion(ev.getVersion());
            } else {
                vo.setEvaluatorName("未知");
            }

            // 计算平均分
            BigDecimal avg = entry.getValue().stream()
                    .map(r -> r.getScore() != null ? r.getScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!entry.getValue().isEmpty()) {
                avg = avg.divide(BigDecimal.valueOf(entry.getValue().size()), 2, RoundingMode.HALF_UP);
            }
            vo.setAvgScore(avg);
            vo.setTotalCount(entry.getValue().size());

            overviews.add(vo);
        }
        return overviews;
    }

    @Override
    public Page<EvalExperimentResult> listByExperiment(Long experimentId, Long evaluatorVersionId,
                                                         int pageNum, int pageSize) {
        Page<EvalExperimentResult> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<EvalExperimentResult>()
                .eq(EvalExperimentResult::getExperimentId, experimentId)
                .eq(evaluatorVersionId != null, EvalExperimentResult::getEvaluatorVersionId, evaluatorVersionId)
                .orderByDesc(EvalExperimentResult::getEvaluationTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public void removeByExperimentId(Long experimentId) {
        lambdaUpdate().eq(EvalExperimentResult::getExperimentId, experimentId).remove();
        log.info("[EvalExperimentResult] 批量删除: experimentId={}", experimentId);
    }
}
