package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.askdata.AskDatasetEnhanceDTO;
import com.lightbot.dto.askdata.AskDatasetSaveDTO;
import com.lightbot.dto.askdata.AskDimensionDef;
import com.lightbot.dto.askdata.AskFilterDef;
import com.lightbot.dto.askdata.AskMetricDef;
import com.lightbot.dto.askdata.AskRelationSaveDTO;
import com.lightbot.dto.askdata.AskTenantDimensionDef;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.entity.AskDataset;
import com.lightbot.entity.AskRelation;
import com.lightbot.entity.DataModel;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.AskDatasetMapper;
import com.lightbot.mapper.AskRelationMapper;
import com.lightbot.service.AskDatasetService;
import com.lightbot.service.DataModelService;
import com.lightbot.util.AskDataJdbcUtil;
import com.lightbot.util.DataModelSchemaSupport;
import com.lightbot.vo.AskDatasetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 问数语义层实现
 *
 * @author finch
 * @since 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskDatasetServiceImpl extends ServiceImpl<AskDatasetMapper, AskDataset>
        implements AskDatasetService {

    private static final Set<String> FILTER_OPS = Set.of(
            "eq", "ne", "in", "not_in", "like", "not_like", "starts_with", "not_starts_with",
            "gt", "gte", "lt", "lte", "between", "is_null", "is_not_null", "in_last");

    private final AskRelationMapper askRelationMapper;
    private final DataModelService dataModelService;
    private final DataModelSchemaSupport schemaSupport;
    private final AskDataJdbcUtil askDataJdbcUtil;
    private final ObjectMapper objectMapper;

    @Override
    public List<AskDatasetVO> listAll(String keyword) {
        LambdaQueryWrapper<AskDataset> q = new LambdaQueryWrapper<AskDataset>()
                .orderByDesc(AskDataset::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(AskDataset::getName, kw)
                    .or().like(AskDataset::getCode, kw)
                    .or().like(AskDataset::getDescription, kw));
        }
        return list(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public AskDatasetVO getDetail(Long id) {
        AskDataset ds = getById(id);
        if (ds == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        return toVo(ds);
    }

    @Override
    public AskDatasetVO getByCode(String code) {
        AskDataset ds = getOne(new LambdaQueryWrapper<AskDataset>()
                .eq(AskDataset::getCode, code)
                .last("LIMIT 1"));
        if (ds == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        return toVo(ds);
    }

    @Override
    public AskDataset requireByIdOrCode(String datasetRef) {
        if (!StringUtils.hasText(datasetRef)) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "dataset 不能为空");
        }
        String ref = datasetRef.trim();
        AskDataset ds = null;
        if (ref.matches("^\\d+$")) {
            ds = getById(Long.parseLong(ref));
        }
        if (ds == null) {
            ds = getOne(new LambdaQueryWrapper<AskDataset>()
                    .eq(AskDataset::getCode, ref)
                    .last("LIMIT 1"));
        }
        if (ds == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        return ds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskDatasetVO create(AskDatasetSaveDTO dto) {
        // 1. 校验模型存在
        DataModel model = dataModelService.requireOwned(dto.getDataModelId());
        // 2. 唯一性
        if (count(new LambdaQueryWrapper<AskDataset>().eq(AskDataset::getDataModelId, dto.getDataModelId())) > 0) {
            throw new BizException(ErrorCode.ASK_DATASET_MODEL_BOUND);
        }
        if (count(new LambdaQueryWrapper<AskDataset>().eq(AskDataset::getCode, dto.getCode())) > 0) {
            throw new BizException(ErrorCode.ASK_DATASET_CODE_EXISTS, dto.getCode());
        }
        // 3. 未填维度/指标时从模型自动灌入（模型即可问）
        fillAutoSemanticIfEmpty(dto, model);
        validateSemantic(dto, model);

        AskDataset entity = new AskDataset();
        applyDto(entity, dto);
        save(entity);
        log.info("[AskData] 创建数据集: id={}, code={}, modelId={}", entity.getId(), entity.getCode(), entity.getDataModelId());
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskDatasetVO ensureFromModel(Long dataModelId) {
        DataModel model = dataModelService.requireOwned(dataModelId);
        AskDataset existing = getOne(new LambdaQueryWrapper<AskDataset>()
                .eq(AskDataset::getDataModelId, dataModelId)
                .last("LIMIT 1"));
        if (existing != null) {
            // 已存在：若维度为空则补同步；有维度则直接返回
            List<AskDimensionDef> dims = readList(existing.getDimensions(), AskDimensionDef.class);
            if (dims.isEmpty()) {
                return syncFromModel(existing.getId());
            }
            return toVo(existing);
        }
        AskDatasetSaveDTO dto = new AskDatasetSaveDTO();
        dto.setDataModelId(dataModelId);
        dto.setCode(deriveCode(model));
        dto.setName(model.getName());
        dto.setDescription(model.getDescription());
        dto.setDefaultTimeField("createTime");
        dto.setDefaultFilters(new ArrayList<>());
        dto.setSensitiveFields(new ArrayList<>());
        fillAutoSemanticIfEmpty(dto, model);
        AskDatasetVO created = create(dto);
        // 开启即后台刷画像（失败不影响启用）
        try {
            return refreshProfile(created.getId());
        } catch (Exception e) {
            log.warn("[AskData] 启用后刷新画像失败: modelId={}, err={}", dataModelId, e.getMessage());
            return created;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskDatasetVO syncFromModel(Long id) {
        AskDataset entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        DataModel model = dataModelService.requireOwned(entity.getDataModelId());
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());

        List<AskDimensionDef> oldDims = readList(entity.getDimensions(), AskDimensionDef.class);
        Map<String, AskDimensionDef> oldByKey = new LinkedHashMap<>();
        for (AskDimensionDef d : oldDims) {
            if (d.getFieldKey() != null) {
                oldByKey.put(d.getFieldKey(), d);
            }
        }
        List<AskDimensionDef> newDims = buildAutoDimensions(schema);
        for (AskDimensionDef d : newDims) {
            AskDimensionDef old = oldByKey.get(d.getFieldKey());
            if (old != null && old.getSynonyms() != null && !old.getSynonyms().isEmpty()) {
                d.setSynonyms(old.getSynonyms());
            }
            if (old != null && Boolean.TRUE.equals(old.getHighCardinality())) {
                d.setHighCardinality(true);
            }
        }

        List<AskMetricDef> oldMetrics = readList(entity.getMetrics(), AskMetricDef.class);
        List<AskMetricDef> custom = oldMetrics.stream()
                .filter(m -> m.getCode() != null && !isAutoMetricCode(m.getCode()))
                .collect(Collectors.toList());
        List<AskMetricDef> auto = buildAutoMetrics(schema);
        // 自定义优先：同 code 不覆盖
        Set<String> customCodes = custom.stream()
                .map(m -> m.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<AskMetricDef> merged = new ArrayList<>(custom);
        for (AskMetricDef m : auto) {
            if (!customCodes.contains(m.getCode().toLowerCase(Locale.ROOT))) {
                merged.add(m);
            }
        }

        if (!StringUtils.hasText(entity.getName())) {
            entity.setName(model.getName());
        }
        if (!StringUtils.hasText(entity.getDescription()) && StringUtils.hasText(model.getDescription())) {
            entity.setDescription(model.getDescription());
        }
        if (!StringUtils.hasText(entity.getDefaultTimeField())) {
            entity.setDefaultTimeField("createTime");
        }
        try {
            entity.setDimensions(objectMapper.writeValueAsString(newDims));
            entity.setMetrics(objectMapper.writeValueAsString(merged));
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "同步序列化失败");
        }
        updateById(entity);
        log.info("[AskData] 已从模型同步: datasetId={}, modelId={}, dims={}, metrics={}",
                id, model.getId(), newDims.size(), merged.size());
        return toVo(entity);
    }

    @Override
    public AskDatasetVO findByDataModelId(Long dataModelId) {
        if (dataModelId == null) {
            return null;
        }
        AskDataset ds = getOne(new LambdaQueryWrapper<AskDataset>()
                .eq(AskDataset::getDataModelId, dataModelId)
                .last("LIMIT 1"));
        return ds == null ? null : toVo(ds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskDatasetVO update(Long id, AskDatasetSaveDTO dto) {
        AskDataset entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        DataModel model = dataModelService.requireOwned(dto.getDataModelId());
        if (!Objects.equals(entity.getDataModelId(), dto.getDataModelId())) {
            if (count(new LambdaQueryWrapper<AskDataset>()
                    .eq(AskDataset::getDataModelId, dto.getDataModelId())
                    .ne(AskDataset::getId, id)) > 0) {
                throw new BizException(ErrorCode.ASK_DATASET_MODEL_BOUND);
            }
        }
        if (!Objects.equals(entity.getCode(), dto.getCode())
                && count(new LambdaQueryWrapper<AskDataset>().eq(AskDataset::getCode, dto.getCode())) > 0) {
            throw new BizException(ErrorCode.ASK_DATASET_CODE_EXISTS, dto.getCode());
        }
        validateSemantic(dto, model);
        applyDto(entity, dto);
        updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskDatasetVO updateEnhancement(Long id, AskDatasetEnhanceDTO dto) {
        // 1. 校验数据集与模型归属
        AskDataset entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        DataModel model = dataModelService.requireOwned(entity.getDataModelId());
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        Set<String> fieldKeys = schemaSupport.customFields(schema).stream()
                .map(DataModelSchema.FieldDef::getKey)
                .collect(Collectors.toCollection(HashSet::new));
        fieldKeys.add("id");
        fieldKeys.add("createTime");
        fieldKeys.add("updateTime");

        // 2. 基础增强字段
        String timeField = dto.getDefaultTimeField();
        if (StringUtils.hasText(timeField) && !fieldKeys.contains(timeField)) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "默认时间字段不存在: " + timeField);
        }
        List<String> sensitive = dto.getSensitiveFields() != null ? dto.getSensitiveFields() : List.of();
        for (String key : sensitive) {
            if (StringUtils.hasText(key) && !fieldKeys.contains(key)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "敏感字段不存在: " + key);
            }
        }
        List<AskFilterDef> defaultFilters = normalizeFilters(dto.getDefaultFilters(), fieldKeys);
        Map<String, AskTenantDimensionDef> tenantDimensions =
                normalizeTenantDimensions(dto.getTenantDimensions(), fieldKeys);

        // 3. 自定义业务指标与自动指标合并（保留 cnt / sum_* / avg_*）
        List<AskMetricDef> custom = normalizeCustomMetrics(dto.getCustomMetrics(), fieldKeys);
        List<AskMetricDef> current = readList(entity.getMetrics(), AskMetricDef.class);
        List<AskMetricDef> auto = current.stream()
                .filter(m -> m.getCode() != null && isAutoMetricCode(m.getCode()))
                .collect(Collectors.toList());
        if (auto.isEmpty()) {
            auto = buildAutoMetrics(schema);
        }
        Set<String> customCodes = custom.stream()
                .map(m -> m.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<AskMetricDef> merged = new ArrayList<>(custom);
        for (AskMetricDef m : auto) {
            if (!customCodes.contains(m.getCode().toLowerCase(Locale.ROOT))) {
                merged.add(m);
            }
        }

        entity.setDescription(dto.getDescription());
        entity.setDefaultTimeField(StringUtils.hasText(timeField) ? timeField : "createTime");
        try {
            entity.setSensitiveFields(objectMapper.writeValueAsString(sensitive));
            entity.setDefaultFilters(objectMapper.writeValueAsString(defaultFilters));
            entity.setMetrics(objectMapper.writeValueAsString(merged));
            entity.setTenantDimensions(objectMapper.writeValueAsString(tenantDimensions));
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "增强配置序列化失败");
        }
        updateById(entity);
        return toVo(entity);
    }

    /**
     * 规范化过滤条件（字段 + 算子 + 值）
     */
    private List<AskFilterDef> normalizeFilters(List<AskFilterDef> input, Set<String> fieldKeys) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<AskFilterDef> out = new ArrayList<>();
        for (AskFilterDef raw : input) {
            if (raw == null || !StringUtils.hasText(raw.getField())) {
                continue;
            }
            String field = raw.getField().trim();
            if (!fieldKeys.contains(field)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "过滤字段不存在: " + field);
            }
            String op = StringUtils.hasText(raw.getOp())
                    ? raw.getOp().trim().toLowerCase(Locale.ROOT) : "eq";
            if (!FILTER_OPS.contains(op)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的过滤算子: " + op);
            }
            if (("is_null".equals(op) || "is_not_null".equals(op))) {
                AskFilterDef f = new AskFilterDef();
                f.setField(field);
                f.setOp(op);
                f.setValue(null);
                out.add(f);
                continue;
            }
            if (raw.getValue() == null || "".equals(String.valueOf(raw.getValue()).trim())) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "过滤条件缺少值: " + field + " " + op);
            }
            Object value = raw.getValue();
            if ("in".equals(op) || "not_in".equals(op) || "between".equals(op)) {
                value = coerceListValue(value);
                if ("between".equals(op) && ((List<?>) value).size() < 2) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "between 需要两个值");
                }
            }
            AskFilterDef f = new AskFilterDef();
            f.setField(field);
            f.setOp(op);
            f.setValue(value);
            out.add(f);
        }
        return out;
    }

    private Object coerceListValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof String s) {
            String[] parts = s.split("[,，]");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                if (StringUtils.hasText(p)) {
                    list.add(p.trim());
                }
            }
            return list;
        }
        return List.of(value);
    }

    /**
     * 校验并规范化自定义业务指标（轻量增强用）
     */
    private List<AskMetricDef> normalizeCustomMetrics(List<AskMetricDef> input, Set<String> fieldKeys) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> ops = Set.of("count", "count_distinct", "sum", "avg", "min", "max");
        Set<String> codes = new HashSet<>();
        List<AskMetricDef> out = new ArrayList<>();
        for (AskMetricDef raw : input) {
            if (raw == null) {
                continue;
            }
            String code = raw.getCode() == null ? "" : raw.getCode().trim().toLowerCase(Locale.ROOT);
            String name = raw.getName() == null ? "" : raw.getName().trim();
            if (!StringUtils.hasText(code) || !code.matches("^[a-z][a-z0-9_]{0,63}$")) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "指标编码须小写字母开头，仅含小写字母数字下划线");
            }
            if (isAutoMetricCode(code)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "指标编码与系统自动指标冲突: " + code);
            }
            if (!codes.add(code)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标编码重复: " + code);
            }
            if (!StringUtils.hasText(name)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标名称不能为空");
            }
            String op = StringUtils.hasText(raw.getOp())
                    ? raw.getOp().trim().toLowerCase(Locale.ROOT) : "count";
            if (!ops.contains(op)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的聚合方式: " + op);
            }
            String field = StringUtils.hasText(raw.getField()) ? raw.getField().trim() : null;
            if (!"count".equals(op) && !StringUtils.hasText(field)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "指标 " + code + " 需要指定聚合字段");
            }
            if (StringUtils.hasText(field) && !fieldKeys.contains(field)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标字段不存在: " + field);
            }
            List<AskFilterDef> filters = normalizeFilters(raw.getFilters(), fieldKeys);
            AskMetricDef m = new AskMetricDef();
            m.setCode(code);
            m.setName(name);
            m.setDescription(raw.getDescription());
            m.setOp(op);
            m.setField(field);
            m.setFilters(filters);
            m.setFormat(StringUtils.hasText(raw.getFormat()) ? raw.getFormat() : "number");
            m.setSynonyms(raw.getSynonyms() != null ? raw.getSynonyms() : new ArrayList<>());
            out.add(m);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AskDataset entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        removeById(id);
        askRelationMapper.delete(new LambdaQueryWrapper<AskRelation>()
                .eq(AskRelation::getFromDatasetId, id)
                .or()
                .eq(AskRelation::getToDatasetId, id));
    }

    @Override
    public AskDatasetVO refreshProfile(Long id) {
        AskDataset entity = getById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        DataModel model = dataModelService.requireOwned(entity.getDataModelId());
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        List<AskDimensionDef> dims = readList(entity.getDimensions(), AskDimensionDef.class);
        // 无维度时用模型字段生成临时画像维度
        if (dims.isEmpty()) {
            dims = schemaSupport.customFields(schema).stream().map(f -> {
                AskDimensionDef d = new AskDimensionDef();
                d.setFieldKey(f.getKey());
                d.setName(f.getLabel());
                d.setType("number".equals(f.getType()) || "date".equals(f.getType()) || "datetime".equals(f.getType())
                        ? ("number".equals(f.getType()) ? "categorical" : "time")
                        : "categorical");
                return d;
            }).limit(12).toList();
        }
        Map<String, Object> profile = askDataJdbcUtil.buildProfile(model.getTableName(), schema, dims);
        try {
            entity.setProfileJson(objectMapper.writeValueAsString(profile));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        updateById(entity);
        return toVo(entity);
    }

    @Override
    public List<AskRelation> listRelations() {
        return askRelationMapper.selectList(new LambdaQueryWrapper<AskRelation>()
                .orderByDesc(AskRelation::getUpdateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AskRelation createRelation(AskRelationSaveDTO dto) {
        if (getById(dto.getFromDatasetId()) == null || getById(dto.getToDatasetId()) == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        AskRelation rel = new AskRelation();
        rel.setName(dto.getName());
        rel.setFromDatasetId(dto.getFromDatasetId());
        rel.setFromField(dto.getFromField());
        rel.setToDatasetId(dto.getToDatasetId());
        rel.setToField(dto.getToField());
        askRelationMapper.insert(rel);
        return rel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long id) {
        AskRelation rel = askRelationMapper.selectById(id);
        if (rel == null) {
            throw new BizException(ErrorCode.ASK_RELATION_NOT_FOUND);
        }
        askRelationMapper.deleteById(id);
    }

    @Override
    public List<AskDatasetVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<AskDataset>().in(AskDataset::getId, ids)).stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    private void fillAutoSemanticIfEmpty(AskDatasetSaveDTO dto, DataModel model) {
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        if (dto.getDimensions() == null || dto.getDimensions().isEmpty()) {
            dto.setDimensions(buildAutoDimensions(schema));
        }
        if (dto.getMetrics() == null || dto.getMetrics().isEmpty()) {
            dto.setMetrics(buildAutoMetrics(schema));
        }
        if (!StringUtils.hasText(dto.getDefaultTimeField())) {
            dto.setDefaultTimeField("createTime");
        }
        if (!StringUtils.hasText(dto.getDescription()) && StringUtils.hasText(model.getDescription())) {
            dto.setDescription(model.getDescription());
        }
    }

    private List<AskDimensionDef> buildAutoDimensions(DataModelSchema schema) {
        List<AskDimensionDef> dims = new ArrayList<>();
        // 系统时间维度
        AskDimensionDef createTime = new AskDimensionDef();
        createTime.setFieldKey("createTime");
        createTime.setName("创建时间");
        createTime.setType("time");
        createTime.setTimeGrain("day");
        createTime.setSynonyms(List.of("时间", "日期"));
        dims.add(createTime);

        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            AskDimensionDef d = new AskDimensionDef();
            d.setFieldKey(f.getKey());
            d.setName(StringUtils.hasText(f.getLabel()) ? f.getLabel() : f.getKey());
            d.setType(inferDimType(f.getType()));
            if ("time".equals(d.getType())) {
                d.setTimeGrain("day");
            }
            List<String> syn = new ArrayList<>();
            if (StringUtils.hasText(f.getDescription())) {
                // 描述不直接当同义词，留给 describe；同义词留给用户增强
            }
            d.setSynonyms(syn);
            // 把 description 塞进 name 不够；describe 时会带模型字段。维度侧用 label 即可
            dims.add(d);
        }
        return dims;
    }

    private List<AskMetricDef> buildAutoMetrics(DataModelSchema schema) {
        List<AskMetricDef> metrics = new ArrayList<>();
        AskMetricDef cnt = new AskMetricDef();
        cnt.setCode("cnt");
        cnt.setName("数量");
        cnt.setOp("count");
        cnt.setFormat("integer");
        cnt.setSynonyms(new ArrayList<>(List.of("总数", "条数", "多少")));
        cnt.setDescription("记录条数");
        metrics.add(cnt);

        for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
            if (!"number".equalsIgnoreCase(f.getType())) {
                continue;
            }
            String label = StringUtils.hasText(f.getLabel()) ? f.getLabel() : f.getKey();
            AskMetricDef sum = new AskMetricDef();
            sum.setCode("sum_" + f.getKey());
            sum.setName(label + "合计");
            sum.setOp("sum");
            sum.setField(f.getKey());
            sum.setFormat("number");
            sum.setSynonyms(new ArrayList<>(List.of(label + "总和", label + "总计")));
            sum.setDescription(f.getDescription());
            metrics.add(sum);

            AskMetricDef avg = new AskMetricDef();
            avg.setCode("avg_" + f.getKey());
            avg.setName(label + "平均");
            avg.setOp("avg");
            avg.setField(f.getKey());
            avg.setFormat("number");
            avg.setSynonyms(new ArrayList<>(List.of(label + "均值")));
            avg.setDescription(f.getDescription());
            metrics.add(avg);
        }
        return metrics;
    }

    private String inferDimType(String fieldType) {
        if (fieldType == null) {
            return "categorical";
        }
        return switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "date", "datetime" -> "time";
            default -> "categorical";
        };
    }

    private boolean isAutoMetricCode(String code) {
        if (code == null) {
            return false;
        }
        String c = code.toLowerCase(Locale.ROOT);
        return "cnt".equals(c) || c.startsWith("sum_") || c.startsWith("avg_");
    }

    private String deriveCode(DataModel model) {
        String table = model.getTableName();
        if (StringUtils.hasText(table) && table.startsWith(DataModelSchemaSupport.TABLE_PREFIX)) {
            String suffix = table.substring(DataModelSchemaSupport.TABLE_PREFIX.length());
            if (suffix.matches("^[a-z][a-z0-9_]{0,63}$")) {
                // 保证全局唯一：若 code 已被其它数据集占用则加后缀
                if (count(new LambdaQueryWrapper<AskDataset>().eq(AskDataset::getCode, suffix)) == 0) {
                    return suffix;
                }
                return suffix + "_" + model.getId() % 10000;
            }
        }
        return "model_" + model.getId();
    }

    private void validateSemantic(AskDatasetSaveDTO dto, DataModel model) {
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        Set<String> fieldKeys = schemaSupport.customFields(schema).stream()
                .map(DataModelSchema.FieldDef::getKey)
                .collect(Collectors.toCollection(HashSet::new));
        fieldKeys.add("id");
        fieldKeys.add("createTime");
        fieldKeys.add("updateTime");

        if (StringUtils.hasText(dto.getDefaultTimeField()) && !fieldKeys.contains(dto.getDefaultTimeField())) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "默认时间字段不存在: " + dto.getDefaultTimeField());
        }
        Set<String> metricCodes = new HashSet<>();
        if (dto.getMetrics() != null) {
            for (AskMetricDef m : dto.getMetrics()) {
                if (!StringUtils.hasText(m.getCode())) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标 code 不能为空");
                }
                if (!metricCodes.add(m.getCode().toLowerCase(Locale.ROOT))) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标 code 重复: " + m.getCode());
                }
                if (StringUtils.hasText(m.getField()) && !fieldKeys.contains(m.getField())) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "指标字段不存在: " + m.getField());
                }
            }
        }
        if (dto.getDimensions() != null) {
            for (AskDimensionDef d : dto.getDimensions()) {
                if (!StringUtils.hasText(d.getFieldKey()) || !fieldKeys.contains(d.getFieldKey())) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "维度字段不存在: " + d.getFieldKey());
                }
            }
        }
        normalizeTenantDimensions(dto.getTenantDimensions(), fieldKeys);
    }

    /**
     * 规范化租户维度映射；兼容值为字段名字符串或 {field,match} 对象
     *
     * @param input     原始映射
     * @param fieldKeys 数据模型可用字段
     * @return 规范化后的映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, AskTenantDimensionDef> normalizeTenantDimensions(Map<String, ?> input,
                                                                         Set<String> fieldKeys) {
        if (input == null || input.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Set<String> allowedCallerKeys = Set.of("regionId", "enterpriseId");
        Set<String> allowedMatch = Set.of("eq", "prefix", "in", "subtree");
        Map<String, AskTenantDimensionDef> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : input.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String callerKey = e.getKey().trim();
            // 废弃 externalUserId 问数维度：读旧配置时跳过，写新配置时拒绝其它未知键
            if ("externalUserId".equals(callerKey)) {
                continue;
            }
            if (!allowedCallerKeys.contains(callerKey)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "tenantDimensions 键仅支持 regionId/enterpriseId: " + callerKey);
            }
            String fieldKey;
            String match = "eq";
            Object raw = e.getValue();
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    continue;
                }
                fieldKey = s.trim();
            } else if (raw instanceof Map<?, ?> obj) {
                Object f = obj.get("field");
                if (f == null || !StringUtils.hasText(String.valueOf(f))) {
                    continue;
                }
                fieldKey = String.valueOf(f).trim();
                Object m = obj.get("match");
                if (m != null && StringUtils.hasText(String.valueOf(m))) {
                    match = String.valueOf(m).trim().toLowerCase(Locale.ROOT);
                }
            } else if (raw instanceof AskTenantDimensionDef def) {
                if (!StringUtils.hasText(def.getField())) {
                    continue;
                }
                fieldKey = def.getField().trim();
                if (StringUtils.hasText(def.getMatch())) {
                    match = def.getMatch().trim().toLowerCase(Locale.ROOT);
                }
            } else {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "tenantDimensions." + callerKey + " 格式无效");
            }
            if (!fieldKeys.contains(fieldKey)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "tenantDimensions 字段不存在: " + fieldKey);
            }
            if (!allowedMatch.contains(match)) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                        "tenantDimensions.match 仅支持 eq/subtree/prefix/in: " + match);
            }
            out.put(callerKey, new AskTenantDimensionDef(fieldKey, match));
        }
        return out;
    }

    private void applyDto(AskDataset entity, AskDatasetSaveDTO dto) {
        entity.setDataModelId(dto.getDataModelId());
        entity.setCode(dto.getCode().trim());
        entity.setName(dto.getName().trim());
        entity.setDescription(dto.getDescription());
        entity.setDefaultTimeField(dto.getDefaultTimeField());
        try {
            entity.setDefaultFilters(objectMapper.writeValueAsString(
                    dto.getDefaultFilters() != null ? dto.getDefaultFilters() : List.of()));
            entity.setSensitiveFields(objectMapper.writeValueAsString(
                    dto.getSensitiveFields() != null ? dto.getSensitiveFields() : List.of()));
            entity.setDimensions(objectMapper.writeValueAsString(
                    dto.getDimensions() != null ? dto.getDimensions() : List.of()));
            entity.setMetrics(objectMapper.writeValueAsString(
                    dto.getMetrics() != null ? dto.getMetrics() : List.of()));
            // validateSemantic 已校验；此处用映射内字段作合法集，写成统一 {field,match} 结构
            Map<String, AskTenantDimensionDef> tenant = normalizeTenantDimensions(
                    dto.getTenantDimensions(), permissiveFieldKeysFromTenantInput(dto.getTenantDimensions()));
            entity.setTenantDimensions(objectMapper.writeValueAsString(tenant));
            if (entity.getProfileJson() == null) {
                entity.setProfileJson("{}");
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "配置序列化失败");
        }
    }

    private AskDatasetVO toVo(AskDataset entity) {
        AskDatasetVO vo = new AskDatasetVO();
        vo.setId(entity.getId());
        vo.setDataModelId(entity.getDataModelId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setDefaultTimeField(entity.getDefaultTimeField());
        vo.setDefaultFilters(readFilterList(entity.getDefaultFilters()));
        vo.setSensitiveFields(readStringList(entity.getSensitiveFields()));
        vo.setDimensions(readList(entity.getDimensions(), AskDimensionDef.class));
        vo.setMetrics(readList(entity.getMetrics(), AskMetricDef.class));
        vo.setProfile(readMap(entity.getProfileJson()));
        vo.setTenantDimensions(readTenantDimensions(entity.getTenantDimensions()));
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        try {
            DataModel model = dataModelService.getById(entity.getDataModelId());
            if (model != null) {
                vo.setDataModelName(model.getName());
                vo.setTableName(model.getTableName());
                DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
                List<Map<String, Object>> fields = new ArrayList<>();
                for (DataModelSchema.FieldDef f : schemaSupport.customFields(schema)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", f.getKey());
                    row.put("label", f.getLabel());
                    row.put("description", f.getDescription());
                    row.put("type", f.getType());
                    fields.add(row);
                }
                vo.setModelFields(fields);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return vo;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, AskTenantDimensionDef> readTenantDimensions(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return normalizeTenantDimensions(raw, permissiveFieldKeysFromTenantInput(raw));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 用映射自身声明的字段作为合法集合（展示/落库归一化），严格校验在 validateSemantic / updateEnhancement */
    private Set<String> permissiveFieldKeysFromTenantInput(Map<String, ?> raw) {
        Set<String> keys = new HashSet<>();
        if (raw == null) {
            return keys;
        }
        for (Object v : raw.values()) {
            if (v instanceof String s && StringUtils.hasText(s)) {
                keys.add(s.trim());
            } else if (v instanceof Map<?, ?> m && m.get("field") != null
                    && StringUtils.hasText(String.valueOf(m.get("field")))) {
                keys.add(String.valueOf(m.get("field")).trim());
            } else if (v instanceof AskTenantDimensionDef def && StringUtils.hasText(def.getField())) {
                keys.add(def.getField().trim());
            }
        }
        // 空映射时给占位，避免 normalize 因 fieldKeys 为空误伤（实际无条目可写）
        if (keys.isEmpty()) {
            keys.add("__none__");
        }
        return keys;
    }

    /** 兼容旧版 defaultFilters 对象 Map（字段→等值） */
    private List<AskFilterDef> readFilterList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AskFilterDef>>() {});
        } catch (Exception ignored) {
            // fall through: try map format
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<AskFilterDef> list = new ArrayList<>();
            if (map != null) {
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    AskFilterDef f = new AskFilterDef();
                    f.setField(e.getKey());
                    f.setOp("eq");
                    f.setValue(e.getValue());
                    list.add(f);
                }
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private <T> List<T> readList(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
