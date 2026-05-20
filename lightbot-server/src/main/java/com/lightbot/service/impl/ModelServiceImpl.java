package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.ModelRequest;
import com.lightbot.entity.Model;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ModelType;
import com.lightbot.mapper.ModelMapper;
import com.lightbot.service.ModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model>
        implements ModelService {

    @Override
    public Model create(ModelRequest request) {
        // 1. 校验同一提供商下模型标识不重复
        long count = count(new LambdaQueryWrapper<Model>()
                .eq(Model::getProviderId, request.getProviderId())
                .eq(Model::getModelId, request.getModelId()));
        if (count > 0) {
            throw new BizException(ErrorCode.MODEL_ALREADY_EXISTS);
        }
        // 2. 构建实体并保存
        Model model = new Model();
        model.setProviderId(request.getProviderId());
        model.setModelId(request.getModelId());
        model.setName(request.getName());
        model.setType(request.getType());
        model.setStatus(CommonStatus.ACTIVE);
        save(model);
        return model;
    }

    @Override
    public List<Model> listByProviderId(Long providerId) {
        return list(new LambdaQueryWrapper<Model>()
                .eq(Model::getProviderId, providerId)
                .orderByAsc(Model::getType)
                .orderByAsc(Model::getModelId));
    }

    @Override
    public List<Model> listByType(ModelType type) {
        return list(new LambdaQueryWrapper<Model>()
                .eq(Model::getType, type)
                .eq(Model::getStatus, CommonStatus.ACTIVE)
                .orderByAsc(Model::getProviderId)
                .orderByAsc(Model::getModelId));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MODEL_NOT_FOUND);
        }
    }
}
