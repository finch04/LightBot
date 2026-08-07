package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.datacenter.DataModelCreateDTO;
import com.lightbot.dto.datacenter.DataModelFieldKeySuggestDTO;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.dto.datacenter.DataModelUpdateDTO;
import com.lightbot.entity.DataModel;
import com.lightbot.vo.DataModelFieldKeySuggestVO;
import com.lightbot.vo.DataModelVO;

import java.util.List;

/**
 * 数据模型元数据服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface DataModelService extends IService<DataModel> {

    List<DataModelVO> listMine(Long categoryId, String keyword);

    /**
     * 按分类 ID 列表解析当前全部数据模型 ID（问数按分类绑定时展开）
     *
     * @param categoryIds 分类 ID 列表
     * @return 模型 ID（去重、有序）
     */
    List<Long> listIdsByCategoryIds(List<Long> categoryIds);

    /**
     * 由模型 ID 反查所属分类（兼容旧版按模型绑定迁移）
     *
     * @param modelIds 模型 ID 列表
     * @return 去重后的分类 ID
     */
    List<Long> listCategoryIdsByModelIds(List<Long> modelIds);

    DataModelVO getMine(Long id);

    /**
     * 供数据池内部加载（校验归属）
     */
    DataModel requireOwned(Long id);

    /**
     * 按物理表名加载当前用户拥有的数据模型（开放 API 使用）。
     *
     * @param tableName 完整表名，如 sjc_data_customer
     * @return 数据模型
     */
    DataModel requireOwnedByTableName(String tableName);

    DataModelVO create(DataModelCreateDTO dto);

    DataModelVO updateInfo(Long id, DataModelUpdateDTO dto);

    /**
     * 保存 schema 并对比同步物理表结构/索引
     */
    DataModelVO updateSchema(Long id, DataModelSchema schema);

    /**
     * AI 补全字段英文名（仅针对请求中给出的中文名列表）
     *
     * @param dto 待补全显示名与已占用英文名
     * @return 与 names 顺序对应的英文名
     */
    DataModelFieldKeySuggestVO suggestFieldKeys(DataModelFieldKeySuggestDTO dto);

    void delete(Long id);
}
