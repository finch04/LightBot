package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalDatasetItem;

import java.util.List;

/**
 * 评测数据集条目服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalDatasetItemService extends IService<EvalDatasetItem> {

    /**
     * 创建单条数据集条目
     *
     * @param datasetId   数据集ID
     * @param dataContent 数据内容（JSON）
     * @return 数据集条目实体
     */
    EvalDatasetItem create(Long datasetId, String dataContent);

    /**
     * 批量创建数据集条目
     *
     * @param datasetId    数据集ID
     * @param dataContents 数据内容列表（JSON）
     * @return 创建成功的条目数量
     */
    int batchCreate(Long datasetId, List<String> dataContents);

    /**
     * 分页查询指定数据集的条目（不依赖版本）
     *
     * @param datasetId 数据集ID
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页结果
     */
    Page<EvalDatasetItem> listByDatasetId(Long datasetId, int pageNum, int pageSize);

    /**
     * 查询指定数据集的所有条目（用于创建版本时快照）
     *
     * @param datasetId 数据集ID
     * @return 条目列表
     */
    List<EvalDatasetItem> listAllByDatasetId(Long datasetId);

    /**
     * 根据ID列表查询数据集条目
     *
     * @param ids 条目ID列表
     * @return 条目列表
     */
    List<EvalDatasetItem> listByIds(List<Long> ids);

    /**
     * 删除数据集条目
     *
     * @param id 主键ID
     */
    void deleteById(Long id);
}
