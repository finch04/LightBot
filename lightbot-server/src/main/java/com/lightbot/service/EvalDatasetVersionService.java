package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalDatasetItem;
import com.lightbot.entity.EvalDatasetVersion;

import java.util.List;

/**
 * 评测数据集版本服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalDatasetVersionService extends IService<EvalDatasetVersion> {

    /**
     * 创建数据集版本
     *
     * @param datasetId 数据集ID
     * @param version   版本号
     * @return 数据集版本实体
     */
    EvalDatasetVersion create(Long datasetId, String version);

    /**
     * 查询指定数据集的所有版本
     *
     * @param datasetId 数据集ID
     * @return 版本列表
     */
    List<EvalDatasetVersion> listByDatasetId(Long datasetId);

    /**
     * 获取版本的数据项快照
     *
     * @param versionId 版本ID
     * @return 数据项列表
     */
    List<EvalDatasetItem> getItemsByVersionId(Long versionId);
}
