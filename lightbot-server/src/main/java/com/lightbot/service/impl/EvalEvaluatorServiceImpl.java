package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.dto.EvalEvaluatorExampleVO;
import com.lightbot.entity.EvalEvaluator;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.EvalEvaluatorMapper;
import com.lightbot.service.EvalEvaluatorService;
import com.lightbot.service.EvalEvaluatorVersionService;
import com.lightbot.util.EvalEvaluatorExampleTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;

import java.util.List;

/**
 * 评测器服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
public class EvalEvaluatorServiceImpl extends ServiceImpl<EvalEvaluatorMapper, EvalEvaluator>
        implements EvalEvaluatorService {

    private final EvalEvaluatorVersionService evaluatorVersionService;

    public EvalEvaluatorServiceImpl(@Lazy EvalEvaluatorVersionService evaluatorVersionService) {
        this.evaluatorVersionService = evaluatorVersionService;
    }

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_EVAL_EVALUATOR, key = "#id", unless = "#result == null")
    public EvalEvaluator getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_EVALUATOR, allEntries = true)
    public EvalEvaluator create(String name, String description, String tags, Long userId) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<EvalEvaluator>().eq(EvalEvaluator::getName, name));
        if (count > 0) {
            throw new BizException(ErrorCode.EVAL_EVALUATOR_NAME_EXISTS);
        }

        EvalEvaluator evaluator = new EvalEvaluator();
        evaluator.setName(name);
        evaluator.setDescription(description);
        evaluator.setTags(tags);
        evaluator.setUserId(userId);
        save(evaluator);
        return evaluator;
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_EVALUATOR, allEntries = true)
    public void update(Long id, String name, String description, String tags) {
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
        if (tags != null) {
            evaluator.setTags(tags);
        }
        updateById(evaluator);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_EVALUATOR, allEntries = true)
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

    @Override
    public List<EvalEvaluatorExampleVO> listExamples() {
        return EvalEvaluatorExampleTemplates.listExamples();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalEvaluator createFromExample(String key, Long userId) {
        // 1. 获取示例模板数据
        EvalEvaluatorExampleTemplates.ExampleEvaluatorData data = EvalEvaluatorExampleTemplates.getExampleData(key);
        if (data == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 2. 校验名称唯一性（追加后缀避免冲突）
        String name = data.name();
        long count = count(new LambdaQueryWrapper<EvalEvaluator>().eq(EvalEvaluator::getName, name));
        if (count > 0) {
            name = name + " (" + (count + 1) + ")";
        }

        // 3. 创建评估器
        EvalEvaluator evaluator = create(name, data.description(), null, userId);

        // 4. 创建首个版本（v1）
        evaluatorVersionService.create(evaluator.getId(), "v1", data.prompt(), data.variables(), data.modelConfig());

        return evaluator;
    }
}
