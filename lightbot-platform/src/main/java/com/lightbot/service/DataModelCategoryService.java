package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.datacenter.DataModelCategorySaveDTO;
import com.lightbot.entity.DataModelCategory;

import java.util.List;

/**
 * 数据模型分类服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface DataModelCategoryService extends IService<DataModelCategory> {

    /**
     * 当前用户分类列表
     */
    List<DataModelCategory> listMine();

    /**
     * 新建分类
     */
    DataModelCategory create(DataModelCategorySaveDTO dto);

    /**
     * 重命名分类
     */
    DataModelCategory update(Long id, DataModelCategorySaveDTO dto);

    /**
     * 删除分类（分类下无模型时）
     */
    void delete(Long id);
}
