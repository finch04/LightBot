package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.dto.EvalDatasetExampleVO;
import com.lightbot.entity.EvalDataset;
import com.lightbot.entity.EvalDatasetItem;
import com.lightbot.entity.EvalDatasetVersion;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.EvalDatasetVersionStatus;
import com.lightbot.mapper.EvalDatasetMapper;
import com.lightbot.mapper.EvalDatasetVersionMapper;
import com.lightbot.service.EvalDatasetItemService;
import com.lightbot.service.EvalDatasetService;
import com.lightbot.util.EvalDatasetExampleTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

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

    private final EvalDatasetItemService datasetItemService;
    private final EvalDatasetVersionMapper datasetVersionMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_EVAL_DATASET, key = "#id")
    public EvalDataset getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_DATASET, allEntries = true)
    public EvalDataset create(String name, String description, String columnsConfig, Long userId) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<EvalDataset>().eq(EvalDataset::getName, name));
        if (count > 0) {
            throw new BizException(ErrorCode.EVAL_DATASET_NAME_EXISTS);
        }

        EvalDataset dataset = new EvalDataset();
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setColumnsConfig(columnsConfig);
        dataset.setUserId(userId);
        save(dataset);
        return dataset;
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_DATASET, allEntries = true)
    public void update(Long id, String name, String description, String columnsConfig) {
        EvalDataset dataset = getById(id);
        if (dataset == null) {
            throw new BizException(ErrorCode.EVAL_DATASET_NOT_FOUND);
        }
        if (name != null && !name.equals(dataset.getName())) {
            long count = count(new LambdaQueryWrapper<EvalDataset>().eq(EvalDataset::getName, name));
            if (count > 0) {
                throw new BizException(ErrorCode.EVAL_DATASET_NAME_EXISTS);
            }
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
    @CacheEvict(value = RedisCacheConfig.CACHE_EVAL_DATASET, allEntries = true)
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

    @Override
    public List<EvalDatasetExampleVO> listExamples() {
        return EvalDatasetExampleTemplates.listExamples();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalDataset createFromExample(String key, Long userId) {
        // 1. 获取示例模板数据
        EvalDatasetExampleTemplates.ExampleDatasetData data = EvalDatasetExampleTemplates.getExampleData(key);
        if (data == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 2. 校验名称唯一性（追加后缀避免冲突）
        String name = data.name();
        long count = count(new LambdaQueryWrapper<EvalDataset>().eq(EvalDataset::getName, name));
        if (count > 0) {
            name = name + " (" + (count + 1) + ")";
        }

        // 3. 创建评测集
        EvalDataset dataset = create(name, data.description(), data.columnsConfig(), userId);

        // 4. 批量创建示例数据项
        datasetItemService.batchCreate(dataset.getId(), data.dataContents());

        // 5. 自动创建 v1.0 版本（快照当前条目ID）
        List<EvalDatasetItem> items = datasetItemService.listAllByDatasetId(dataset.getId());
        List<Long> itemIds = items.stream().map(EvalDatasetItem::getId).toList();
        String datasetItemsJson;
        try {
            datasetItemsJson = objectMapper.writeValueAsString(itemIds);
        } catch (Exception e) {
            datasetItemsJson = "[]";
        }
        EvalDatasetVersion dv = new EvalDatasetVersion();
        dv.setDatasetId(dataset.getId());
        dv.setVersion("v1.0");
        dv.setDataCount(items.size());
        dv.setStatus(EvalDatasetVersionStatus.DRAFT);
        dv.setDatasetItems(datasetItemsJson);
        datasetVersionMapper.insert(dv);

        return dataset;
    }
}
