package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.EvalDatasetItem;
import com.lightbot.mapper.EvalDatasetItemMapper;
import com.lightbot.service.EvalDatasetItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测数据集条目服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalDatasetItemServiceImpl extends ServiceImpl<EvalDatasetItemMapper, EvalDatasetItem>
        implements EvalDatasetItemService {


    @Override
    @Transactional
    public EvalDatasetItem create(Long datasetId, String dataContent) {
        EvalDatasetItem item = new EvalDatasetItem();
        item.setDatasetId(datasetId);
        item.setDataContent(dataContent);
        save(item);
        return item;
    }

    @Override
    @Transactional
    public int batchCreate(Long datasetId, List<String> dataContents) {
        List<EvalDatasetItem> items = new ArrayList<>();
        for (String content : dataContents) {
            EvalDatasetItem item = new EvalDatasetItem();
            item.setDatasetId(datasetId);
            item.setDataContent(content);
            items.add(item);
        }
        saveBatch(items);
        return items.size();
    }

    @Override
    public Page<EvalDatasetItem> listByDatasetId(Long datasetId, int pageNum, int pageSize) {
        Page<EvalDatasetItem> page = new Page<>(pageNum, pageSize);
        var wrapper = new LambdaQueryWrapper<EvalDatasetItem>()
                .eq(EvalDatasetItem::getDatasetId, datasetId)
                .orderByDesc(EvalDatasetItem::getCreateTime);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<EvalDatasetItem> listAllByDatasetId(Long datasetId) {
        return lambdaQuery()
                .eq(EvalDatasetItem::getDatasetId, datasetId)
                .orderByDesc(EvalDatasetItem::getCreateTime)
                .list();
    }

    @Override
    public List<EvalDatasetItem> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return super.listByIds(ids);
    }

    @Override
    public void deleteById(Long id) {
        removeById(id);
    }
}
