package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.EvalEvaluatorTemplate;
import com.lightbot.mapper.EvalEvaluatorTemplateMapper;
import com.lightbot.service.EvalEvaluatorTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测器模板服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalEvaluatorTemplateServiceImpl extends ServiceImpl<EvalEvaluatorTemplateMapper, EvalEvaluatorTemplate>
        implements EvalEvaluatorTemplateService {

    @Override
    public List<EvalEvaluatorTemplate> listAll() {
        return list();
    }

    @Override
    public EvalEvaluatorTemplate getByKey(String evaluatorTemplateKey) {
        return lambdaQuery().eq(EvalEvaluatorTemplate::getEvaluatorTemplateKey, evaluatorTemplateKey).one();
    }
}
