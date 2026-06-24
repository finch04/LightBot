package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.ModelRequest;
import com.lightbot.entity.Model;
import com.lightbot.enums.ModelType;

import java.util.List;

/**
 * 模型服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface ModelService extends IService<Model> {

    /**
     * 创建模型
     *
     * @param request 创建请求
     * @return 模型
     */
    Model create(ModelRequest request);

    /**
     * 获取指定提供商下的模型列表
     *
     * @param providerId 提供商ID
     * @return 模型列表
     */
    List<Model> listByProviderId(Long providerId);

    /**
     * 获取指定类型的所有可用模型（按提供商分组）
     *
     * @param type 模型类型
     * @return 模型列表
     */
    List<Model> listByType(ModelType type);

    /**
     * 删除模型
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 删除指定提供商的所有模型（级联删除，跳过权限校验）
     *
     * @param providerId 提供商ID
     */
    void deleteByProviderId(Long providerId);
}
