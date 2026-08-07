package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelCreateDTO;
import com.lightbot.dto.datacenter.DataModelFieldKeySuggestDTO;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.dto.datacenter.DataModelUpdateDTO;
import com.lightbot.entity.DataModel;
import com.lightbot.entity.DataModelCategory;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.DataModelCategoryMapper;
import com.lightbot.mapper.DataModelMapper;
import com.lightbot.service.DataModelService;
import com.lightbot.service.port.DataModelFieldKeySuggestPort;
import com.lightbot.util.DataModelDdlUtil;
import com.lightbot.util.DataModelSchemaSupport;
import com.lightbot.vo.DataModelFieldKeySuggestVO;
import com.lightbot.vo.DataModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 数据模型服务：元数据 + 物理表 DDL 同步
 *
 * @author finch
 * @since 2026-07-26
 */
@Service
@RequiredArgsConstructor
public class DataModelServiceImpl extends ServiceImpl<DataModelMapper, DataModel>
        implements DataModelService {

    private final DataModelCategoryMapper categoryMapper;
    private final DataModelSchemaSupport schemaSupport;
    private final DataModelDdlUtil ddlUtil;
    private final ObjectProvider<DataModelFieldKeySuggestPort> fieldKeySuggestPort;

    @Override
    public List<DataModelVO> listMine(Long categoryId, String keyword) {
        // 企业资产：建设者可见全部数据模型
        LambdaQueryWrapper<DataModel> qw = new LambdaQueryWrapper<DataModel>()
                .eq(categoryId != null, DataModel::getCategoryId, categoryId)
                .orderByDesc(DataModel::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(DataModel::getName, keyword.trim())
                    .or().like(DataModel::getDescription, keyword.trim())
                    .or().like(DataModel::getTableName, keyword.trim()));
        }
        return list(qw).stream().map(this::toVo).toList();
    }

    @Override
    public List<Long> listIdsByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<DataModel>()
                .in(DataModel::getCategoryId, categoryIds)
                .select(DataModel::getId)
                .orderByAsc(DataModel::getId))
                .stream()
                .map(DataModel::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> listCategoryIdsByModelIds(List<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (DataModel model : list(new LambdaQueryWrapper<DataModel>()
                .in(DataModel::getId, modelIds)
                .isNotNull(DataModel::getCategoryId)
                .select(DataModel::getCategoryId))) {
            if (model.getCategoryId() != null) {
                categoryIds.add(model.getCategoryId());
            }
        }
        return new ArrayList<>(categoryIds);
    }

    @Override
    public DataModelVO getMine(Long id) {
        return toVo(requireOwned(id));
    }

    @Override
    public DataModel requireOwned(Long id) {
        DataModel model = getById(id);
        if (model == null) {
            throw new BizException(ErrorCode.DATA_MODEL_NOT_FOUND);
        }
        return model;
    }

    @Override
    public DataModel requireOwnedByTableName(String tableName) {
        // 1. 校验表名格式（须为 sjc_data_ 合法后缀）
        schemaSupport.assertSafeTableName(tableName);
        // 2. 企业资产：按表名查询，不再按用户过滤
        DataModel model = getOne(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getTableName, tableName.trim())
                .last("LIMIT 1"));
        if (model == null) {
            throw new BizException(ErrorCode.DATA_MODEL_NOT_FOUND);
        }
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO create(DataModelCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        // 1. 校验分类存在
        requireOwnedCategory(dto.getCategoryId(), userId);
        // 2. 表名唯一
        String tableName = schemaSupport.buildTableName(dto.getTableNameSuffix().trim().toLowerCase());
        long tableExists = count(new LambdaQueryWrapper<DataModel>().eq(DataModel::getTableName, tableName));
        if (tableExists > 0 || ddlUtil.tableExists(tableName)) {
            throw new BizException(ErrorCode.DATA_MODEL_TABLE_EXISTS, tableName);
        }
        // 3. 名称唯一（企业维度）
        long nameExists = count(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getName, dto.getName().trim()));
        if (nameExists > 0) {
            throw new BizException(ErrorCode.DATA_MODEL_NAME_EXISTS);
        }
        // 4. 写元数据 + 建空表（含系统字段）
        DataModelSchema empty = schemaSupport.emptySchema();
        DataModel model = new DataModel();
        model.setUserId(userId);
        model.setCategoryId(dto.getCategoryId());
        model.setName(dto.getName().trim());
        model.setTableName(tableName);
        model.setDescription(dto.getDescription());
        model.setSchemaJson(schemaSupport.toSchemaJson(empty));
        save(model);
        ddlUtil.createTable(tableName, empty);
        // 表备注：名称：描述（与列备注风格一致，供大模型分析）
        ddlUtil.applyTableComment(tableName, model.getName(), model.getDescription());
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO updateInfo(Long id, DataModelUpdateDTO dto) {
        DataModel model = requireOwned(id);
        requireOwnedCategory(dto.getCategoryId(), model.getUserId());
        long nameExists = count(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getName, dto.getName().trim())
                .ne(DataModel::getId, id));
        if (nameExists > 0) {
            throw new BizException(ErrorCode.DATA_MODEL_NAME_EXISTS);
        }
        model.setName(dto.getName().trim());
        model.setCategoryId(dto.getCategoryId());
        model.setDescription(dto.getDescription());
        updateById(model);
        // 名称/描述变更后同步物理表 COMMENT
        ddlUtil.applyTableComment(model.getTableName(), model.getName(), model.getDescription());
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO updateSchema(Long id, DataModelSchema schema) {
        DataModel model = requireOwned(id);
        // 1. 校验 schema
        schemaSupport.validateSchema(schema);
        // 2. 对比并同步物理表 / 索引（传入旧 schema 以支持单字段英文名改名 → RENAME COLUMN）
        DataModelSchema previous = schemaSupport.parseSchema(model.getSchemaJson());
        ddlUtil.syncTable(model.getTableName(), schema, previous);
        // 3. 持久化元数据
        model.setSchemaJson(schemaSupport.toSchemaJson(schema));
        updateById(model);
        return toVo(model);
    }

    @Override
    public DataModelFieldKeySuggestVO suggestFieldKeys(DataModelFieldKeySuggestDTO dto) {
        // 1. 清洗待补全显示名
        List<String> names = new ArrayList<>();
        for (String raw : dto.getNames()) {
            if (!StringUtils.hasText(raw) || !StringUtils.hasText(raw.trim())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "字段显示名不能为空");
            }
            names.add(raw.trim());
        }
        List<String> occupied = dto.getOccupiedKeys() == null
                ? List.of()
                : dto.getOccupiedKeys().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        // 2. 通过 AI Port 生成英文名（平台不直接依赖模型 SDK）
        DataModelFieldKeySuggestPort port = fieldKeySuggestPort.getIfAvailable();
        if (port == null) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        List<String> keys = port.suggestKeys(names, occupied);
        if (keys == null || keys.size() != names.size()) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        return new DataModelFieldKeySuggestVO(keys);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DataModel model = requireOwned(id);
        removeById(id);
        // 物理表一并清理，避免孤儿表占用表名
        ddlUtil.dropTableIfExists(model.getTableName());
    }

    private void requireOwnedCategory(Long categoryId, Long userId) {
        // 企业资产：仅校验分类存在（userId 参数保留兼容调用方）
        DataModelCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.DATA_CATEGORY_NOT_FOUND);
        }
    }

    private DataModelVO toVo(DataModel model) {
        DataModelVO vo = new DataModelVO();
        vo.setId(model.getId());
        vo.setCategoryId(model.getCategoryId());
        vo.setName(model.getName());
        vo.setTableName(model.getTableName());
        vo.setDescription(model.getDescription());
        vo.setSchema(schemaSupport.parseSchema(model.getSchemaJson()));
        vo.setCreateTime(model.getCreateTime());
        vo.setUpdateTime(model.getUpdateTime());
        return vo;
    }
}
