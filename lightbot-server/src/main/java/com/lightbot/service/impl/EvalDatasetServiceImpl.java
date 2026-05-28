package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.EvalDataset;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.EvalDatasetMapper;
import com.lightbot.service.EvalDatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评测数据集服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalDatasetServiceImpl extends ServiceImpl<EvalDatasetMapper, EvalDataset>
        implements EvalDatasetService {

    @Override
    public EvalDataset create(String name, String description, String columnsConfig, Long userId) {
        EvalDataset dataset = new EvalDataset();
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setColumnsConfig(columnsConfig);
        dataset.setUserId(userId);
        save(dataset);
        return dataset;
    }

    @Override
    public void update(Long id, String name, String description, String columnsConfig) {
        EvalDataset dataset = getById(id);
        if (dataset == null) {
            throw new BizException(ErrorCode.EVAL_DATASET_NOT_FOUND);
        }
        if (name != null) {
            dataset.setName(name);
        }
        if (description != null) {
            dataset.setDescription(description);
        }
        if (columnsConfig != null) {
            dataset.setColumnsConfig(columnsConfig);
        }
        updateById(dataset);
    }

    @Override
    public void deleteById(Long id) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.EVAL_DATASET_NOT_FOUND);
        }
        removeById(id);
    }

    @Override
    public Page<EvalDataset> list(int pageNum, int pageSize, String keyword, Long userId) {
        Page<EvalDataset> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<EvalDataset>()
                .eq(userId != null, EvalDataset::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), EvalDataset::getName, keyword)
                .orderByDesc(EvalDataset::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }
}
