package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalEvaluator;
import com.lightbot.entity.EvalEvaluatorVersion;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.EvalVersionStatus;
import com.lightbot.mapper.EvalEvaluatorVersionMapper;
import com.lightbot.service.EvalEvaluatorService;
import com.lightbot.service.EvalEvaluatorVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测器版本服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalEvaluatorVersionServiceImpl extends ServiceImpl<EvalEvaluatorVersionMapper, EvalEvaluatorVersion>
        implements EvalEvaluatorVersionService {

    private final EvalEvaluatorService evaluatorService;

    @Override
    public EvalEvaluatorVersion create(Long evaluatorId, String version, String prompt,
                                        String variables, String modelConfig) {
        EvalEvaluator evaluator = evaluatorService.getById(evaluatorId);
        if (evaluator == null) {
            throw new BizException(ErrorCode.EVAL_EVALUATOR_NOT_FOUND);
        }
        EvalEvaluatorVersion ev = new EvalEvaluatorVersion();
        ev.setEvaluatorId(evaluatorId);
        ev.setVersion(version);
        ev.setPrompt(prompt);
        ev.setVariables(variables);
        ev.setModelConfig(modelConfig);
        ev.setStatus(EvalVersionStatus.DRAFT);
        save(ev);
        return ev;
    }

    @Override
    public List<EvalEvaluatorVersion> listByEvaluatorId(Long evaluatorId) {
        return lambdaQuery()
                .eq(EvalEvaluatorVersion::getEvaluatorId, evaluatorId)
                .orderByDesc(EvalEvaluatorVersion::getCreateTime)
                .list();
    }

    @Override
    public EvalEvaluatorVersion getByEvaluatorIdAndVersion(Long evaluatorId, String version) {
        return lambdaQuery()
                .eq(EvalEvaluatorVersion::getEvaluatorId, evaluatorId)
                .eq(EvalEvaluatorVersion::getVersion, version)
                .one();
    }
}
