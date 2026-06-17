package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalEvaluator;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.EvalEvaluatorMapper;
import com.lightbot.service.EvalEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评测器服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalEvaluatorServiceImpl extends ServiceImpl<EvalEvaluatorMapper, EvalEvaluator>
        implements EvalEvaluatorService {

    @Override
    public EvalEvaluator create(String name, String description, Long userId) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<EvalEvaluator>().eq(EvalEvaluator::getName, name));
        if (count > 0) {
            throw new BizException(ErrorCode.EVAL_EVALUATOR_NAME_EXISTS);
        }

        EvalEvaluator evaluator = new EvalEvaluator();
        evaluator.setName(name);
        evaluator.setDescription(description);
        evaluator.setUserId(userId);
        save(evaluator);
        return evaluator;
    }

    @Override
    public void update(Long id, String name, String description) {
        EvalEvaluator evaluator = getById(id);
        if (evaluator == null) {
            throw new BizException(ErrorCode.EVAL_EVALUATOR_NOT_FOUND);
        }
        if (name != null && !name.equals(evaluator.getName())) {
            long count = count(new LambdaQueryWrapper<EvalEvaluator>().eq(EvalEvaluator::getName, name));
            if (count > 0) {
                throw new BizException(ErrorCode.EVAL_EVALUATOR_NAME_EXISTS);
            }
            evaluator.setName(name);
        }
        if (description != null) {
            evaluator.setDescription(description);
        }
        updateById(evaluator);
    }

    @Override
    public void deleteById(Long id) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.EVAL_EVALUATOR_NOT_FOUND);
        }
        removeById(id);
    }

    @Override
    public Page<EvalEvaluator> list(int pageNum, int pageSize, String keyword, Long userId) {
        Page<EvalEvaluator> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<EvalEvaluator>()
                .eq(userId != null, EvalEvaluator::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), EvalEvaluator::getName, keyword)
                .orderByDesc(EvalEvaluator::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }
}
