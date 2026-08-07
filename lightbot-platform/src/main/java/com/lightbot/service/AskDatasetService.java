package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.askdata.AskDatasetEnhanceDTO;
import com.lightbot.dto.askdata.AskDatasetSaveDTO;
import com.lightbot.dto.askdata.AskRelationSaveDTO;
import com.lightbot.entity.AskDataset;
import com.lightbot.entity.AskRelation;
import com.lightbot.vo.AskDatasetVO;

import java.util.List;

/**
 * 问数语义层：数据集与关联
 *
 * @author finch
 * @since 2026-07-30
 */
public interface AskDatasetService extends IService<AskDataset> {

    List<AskDatasetVO> listAll(String keyword);

    AskDatasetVO getDetail(Long id);

    AskDatasetVO getByCode(String code);

    AskDataset requireByIdOrCode(String datasetRef);

    AskDatasetVO create(AskDatasetSaveDTO dto);

    AskDatasetVO update(Long id, AskDatasetSaveDTO dto);

    /**
     * 轻量问数增强：业务说明 / 默认时间 / 敏感字段 / 默认过滤 / 自定义业务指标
     *
     * @param id  问数数据集 ID
     * @param dto 增强字段
     * @return 更新后的数据集
     */
    AskDatasetVO updateEnhancement(Long id, AskDatasetEnhanceDTO dto);

    void delete(Long id);

    /**
     * 模型即可问：按数据模型确保存在问数配置（无则自动创建并同步字段语义）
     *
     * @param dataModelId 数据模型 ID
     * @return 问数数据集
     */
    AskDatasetVO ensureFromModel(Long dataModelId);

    /**
     * 从模型表单结构同步维度与默认指标（保留自定义指标与同义词）
     *
     * @param id 问数数据集 ID
     * @return 同步后的数据集
     */
    AskDatasetVO syncFromModel(Long id);

    /**
     * 按数据模型 ID 查找问数配置（可空）
     */
    AskDatasetVO findByDataModelId(Long dataModelId);

    /**
     * 刷新字段画像（Top 值 / 样例）
     */
    AskDatasetVO refreshProfile(Long id);

    List<AskRelation> listRelations();

    AskRelation createRelation(AskRelationSaveDTO dto);

    void deleteRelation(Long id);

    /**
     * 按 ID 列表返回 VO（Agent 绑定展示）
     */
    List<AskDatasetVO> listByIds(List<Long> ids);
}
