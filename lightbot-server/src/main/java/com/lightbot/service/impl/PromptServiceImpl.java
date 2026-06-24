package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.entity.Prompt;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.PromptMapper;
import com.lightbot.service.PromptService;
import com.lightbot.service.PromptVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 提示词服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    private final PromptVersionService promptVersionService;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_PROMPT, key = "#id", unless = "#result == null")
    public Prompt getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_PROMPT, key = "#entity.id")
    public boolean updateById(Prompt entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_PROMPT, allEntries = true)
    public Prompt create(String promptKey, String description, String tags, Long userId) {
        // 1. 校验promptKey唯一性
        long count = lambdaQuery().eq(Prompt::getPromptKey, promptKey).count();
        if (count > 0) {
            throw new BizException(ErrorCode.PROMPT_KEY_EXISTS);
        }
        // 2. 创建
        Prompt prompt = new Prompt();
        prompt.setPromptKey(promptKey);
        prompt.setDescription(description);
        prompt.setTags(tags);
        prompt.setUserId(userId);
        save(prompt);
        return prompt;
    }

    @Override
    public void update(Long id, String description, String tags) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BizException(ErrorCode.PROMPT_NOT_FOUND);
        }
        if (description != null) {
            prompt.setDescription(description);
        }
        if (tags != null) {
            prompt.setTags(tags);
        }
        updateById(prompt);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_PROMPT, key = "#id")
    public void deleteById(Long id) {
        Prompt prompt = getById(id);
        if (prompt == null) {
            throw new BizException(ErrorCode.PROMPT_NOT_FOUND);
        }
        // 级联删除所有版本
        try {
            promptVersionService.deleteByPromptKey(prompt.getPromptKey());
        } catch (Exception e) {
            log.warn("[Prompt] 级联删除版本失败, promptKey={}, error={}", prompt.getPromptKey(), e.getMessage());
        }
        removeById(id);
    }

    @Override
    public Page<Prompt> list(int pageNum, int pageSize, String keyword, Long userId) {
        Page<Prompt> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<Prompt>()
                .eq(userId != null, Prompt::getUserId, userId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Prompt::getPromptKey, keyword)
                        .or().like(Prompt::getDescription, keyword))
                .orderByDesc(Prompt::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }
}
