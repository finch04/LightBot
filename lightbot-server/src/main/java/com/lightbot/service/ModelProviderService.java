package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;

/**
 * 模型提供商服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface ModelProviderService extends IService<ModelProvider> {

    /**
     * 创建模型提供商
     *
     * @param request 创建请求
     * @return 模型提供商
     */
    ModelProvider create(ModelProviderRequest request);

    /**
     * 更新模型提供商
     *
     * @param request 更新请求
     * @return 模型提供商
     */
    ModelProvider update(ModelProviderRequest request);

    /**
     * 分页查询
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<ModelProvider> listPage(int pageNum, int pageSize);

    /**
     * 删除模型提供商
     *
     * @param id 主键ID
     */
    void deleteById(Long id);
}
