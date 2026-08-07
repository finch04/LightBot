package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.askdata.AskDataIntentIR;
import com.lightbot.dto.askdata.AskDatasetPreviewDTO;
import com.lightbot.dto.askdata.AskDimensionDef;
import com.lightbot.dto.askdata.AskFilterDef;
import com.lightbot.dto.askdata.AskMetricDef;
import com.lightbot.dto.askdata.AskTenantDimensionDef;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.entity.AskDataset;
import com.lightbot.entity.DataModel;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.AskDataQueryService;
import com.lightbot.service.AskDatasetService;
import com.lightbot.service.DataModelService;
import com.lightbot.service.RegionService;
import com.lightbot.util.AskDataJdbcUtil;
import com.lightbot.util.DataModelSchemaSupport;
import com.lightbot.vo.AskDataResultVO;
import com.lightbot.vo.AskDatasetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 问数查询引擎：解析 IR → 校验白名单 → JDBC 执行 → Insight 结果
 *
 * @author finch
 * @since 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskDataQueryServiceImpl implements AskDataQueryService {

    private final AskDatasetService askDatasetService;
    private final DataModelService dataModelService;
    private final RegionService regionService;
    private final DataModelSchemaSupport schemaSupport;
    private final AskDataJdbcUtil askDataJdbcUtil;
    private final ObjectMapper objectMapper;

    @Override
    public AskDataResultVO execute(AskDataIntentIR ir, Set<Long> allowedDatasetIds) {
        return execute(ir, allowedDatasetIds, null);
    }

    @Override
    public AskDataResultVO execute(AskDataIntentIR ir, Set<Long> allowedDatasetIds, Map<String, String> tenantValues) {
        if (ir == null || !StringUtils.hasText(ir.getDataset())) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "dataset 不能为空");
        }
        AskDataset dataset = askDatasetService.requireByIdOrCode(ir.getDataset());
        if (allowedDatasetIds != null && !allowedDatasetIds.contains(dataset.getId())) {
            throw new BizException(ErrorCode.ASK_DATA_DATASET_FORBIDDEN);
        }
        Map<String, AskTenantDimensionDef> mapping = readTenantDimensions(dataset.getTenantDimensions());
        applyForcedTenantFilters(mapping, ir, tenantValues);
        List<AskFilterDef> defaultFilters = readFilterList(dataset.getDefaultFilters());
        if (!mapping.isEmpty()) {
            Set<String> tenantFields = mapping.values().stream()
                    .map(AskTenantDimensionDef::getField)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            defaultFilters = defaultFilters.stream()
                    .filter(f -> f.getField() == null || !tenantFields.contains(f.getField()))
                    .collect(Collectors.toList());
        }
        return executeInternal(dataset, ir, defaultFilters,
                readList(dataset.getMetrics(), AskMetricDef.class));
    }

    /**
     * 按数据集 tenantDimensions 强制注入隔离过滤：覆盖 IR 同字段条件。
     *
     * <p>角色互斥（由是否传入 enterpriseId 判定）：
     * <ul>
     *   <li>企业用户（有 enterpriseId）：仅应用企业维度，忽略地区</li>
     *   <li>行业用户（无 enterpriseId）：仅应用地区维度，忽略企业；配了地区则 regionId 必填</li>
     * </ul>
     * 不再支持 externalUserId 作为问数租户维度。
     */
    private void applyForcedTenantFilters(Map<String, AskTenantDimensionDef> mapping, AskDataIntentIR ir,
                                          Map<String, String> tenantValues) {
        if (mapping == null || mapping.isEmpty()) {
            return;
        }
        Map<String, String> values = tenantValues != null ? tenantValues : Map.of();
        List<AskDataIntentIR.AskDataFilter> filters = ir.getFilters() != null
                ? new ArrayList<>(ir.getFilters()) : new ArrayList<>();
        boolean enterpriseUser = StringUtils.hasText(values.get("enterpriseId"));
        if (enterpriseUser) {
            // 企业视角：只按企业过滤，即使同时传了 regionId 也不叠加地区条件
            injectTenantFilter(filters, mapping.get("enterpriseId"), "enterpriseId", values, true);
        } else {
            // 行业视角：只按地区过滤；配置了地区映射则必须带 regionId
            injectTenantFilter(filters, mapping.get("regionId"), "regionId", values, true);
        }
        ir.setFilters(filters);
    }

    /**
     * 将单个租户维度注入 filters；required=true 且已配置映射时缺值 fail-closed
     */
    private void injectTenantFilter(List<AskDataIntentIR.AskDataFilter> filters, AskTenantDimensionDef def,
                                    String callerKey, Map<String, String> values, boolean required) {
        if (def == null || !StringUtils.hasText(def.getField())) {
            return;
        }
        String fieldKey = def.getField().trim();
        String value = values.get(callerKey);
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BizException(ErrorCode.ASK_DATA_TENANT_REQUIRED, callerKey);
            }
            return;
        }
        String match = StringUtils.hasText(def.getMatch())
                ? def.getMatch().trim().toLowerCase(Locale.ROOT) : "eq";
        filters.removeIf(f -> fieldKey.equals(resolveFilterField(f)));
        AskDataIntentIR.AskDataFilter forced = new AskDataIntentIR.AskDataFilter();
        forced.setField(fieldKey);
        forced.setDim(fieldKey);
        switch (match) {
            case "subtree" -> {
                List<String> codes = regionService.listSelfAndDescendantCodes(value.trim());
                if (codes.isEmpty()) {
                    throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID,
                            "地区库中不存在区划: " + value.trim());
                }
                forced.setOp("in");
                forced.setValue(codes);
            }
            case "prefix" -> {
                forced.setOp("starts_with");
                forced.setValue(value.trim());
            }
            case "in" -> {
                forced.setOp("in");
                forced.setValue(parseMultiValue(value));
            }
            default -> {
                forced.setOp("eq");
                forced.setValue(value.trim());
            }
        }
        filters.add(forced);
    }

    /** 逗号分隔或 JSON 数组 → List，供 in 过滤 */
    private Object parseMultiValue(String raw) {
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                return objectMapper.readValue(text, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (text.contains(",")) {
            List<String> list = new ArrayList<>();
            for (String part : text.split(",")) {
                if (StringUtils.hasText(part)) {
                    list.add(part.trim());
                }
            }
            return list;
        }
        return List.of(text);
    }

    private Map<String, AskTenantDimensionDef> readTenantDimensions(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, AskTenantDimensionDef> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                // 问数隔离仅保留地区 / 企业；历史 externalUserId 配置忽略
                String callerKey = e.getKey().trim();
                if (!"regionId".equals(callerKey) && !"enterpriseId".equals(callerKey)) {
                    continue;
                }
                AskTenantDimensionDef def = parseTenantDef(e.getValue());
                if (def != null && StringUtils.hasText(def.getField())) {
                    out.put(callerKey, def);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[AskData] 解析 tenant_dimensions 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private AskTenantDimensionDef parseTenantDef(Object raw) {
        if (raw instanceof String s && StringUtils.hasText(s)) {
            return new AskTenantDimensionDef(s.trim(), "eq");
        }
        if (raw instanceof Map<?, ?> obj) {
            Object f = obj.get("field");
            if (f == null || !StringUtils.hasText(String.valueOf(f))) {
                return null;
            }
            String match = "eq";
            Object m = obj.get("match");
            if (m != null && StringUtils.hasText(String.valueOf(m))) {
                match = String.valueOf(m).trim().toLowerCase(Locale.ROOT);
            }
            return new AskTenantDimensionDef(String.valueOf(f).trim(), match);
        }
        if (raw instanceof AskTenantDimensionDef def) {
            return def;
        }
        return null;
    }

    private static String resolveFilterField(AskDataIntentIR.AskDataFilter f) {
        if (f == null) {
            return null;
        }
        if (StringUtils.hasText(f.getField())) {
            return f.getField();
        }
        return f.getDim();
    }

    @Override
    public AskDataResultVO previewEnhancement(Long datasetId, AskDatasetPreviewDTO dto) {
        // 1. 校验数据集归属
        AskDataset dataset = askDatasetService.getById(datasetId);
        if (dataset == null) {
            throw new BizException(ErrorCode.ASK_DATASET_NOT_FOUND);
        }
        dataModelService.requireOwned(dataset.getDataModelId());
        if (dto == null || !StringUtils.hasText(dto.getMode())) {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "mode 不能为空");
        }

        // 2. 组装试跑 IR（覆盖用表单过滤 / 指标，不落库）
        List<AskFilterDef> defaultFilters = dto.getDefaultFilters() != null
                ? dto.getDefaultFilters() : List.of();
        List<AskMetricDef> metrics = readList(dataset.getMetrics(), AskMetricDef.class);
        AskDataIntentIR ir = new AskDataIntentIR();
        ir.setDataset(String.valueOf(dataset.getId()));

        String mode = dto.getMode().trim().toLowerCase(Locale.ROOT);
        if ("metric".equals(mode)) {
            AskMetricDef metric = dto.getMetric();
            if (metric == null || !StringUtils.hasText(metric.getCode())) {
                throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "请提供待测业务指标");
            }
            metrics = upsertMetric(metrics, metric);
            ir.setIntent("aggregate");
            ir.setMetrics(List.of(metric.getCode().trim().toLowerCase(Locale.ROOT)));
            ir.setLimit(dto.getLimit() != null && dto.getLimit() > 0 ? dto.getLimit() : 20);
        } else if ("default_filters".equals(mode)) {
            ir.setIntent("lookup");
            int limit = dto.getLimit() != null && dto.getLimit() > 0 ? Math.min(dto.getLimit(), 20) : 5;
            ir.setPageSize(limit);
            ir.setPageNum(1);
        } else {
            throw new BizException(ErrorCode.ASK_DATA_QUERY_INVALID, "不支持的预览模式: " + dto.getMode());
        }

        AskDataResultVO result = executeInternal(dataset, ir, defaultFilters, metrics);
        List<String> assumptions = result.getAssumptions() != null
                ? new ArrayList<>(result.getAssumptions()) : new ArrayList<>();
        assumptions.add(0, "预览模式（未落库配置）");
        result.setAssumptions(assumptions);
        return result;
    }

    /**
     * 核心执行：可用覆盖的默认过滤与指标列表
     */
    private AskDataResultVO executeInternal(AskDataset dataset, AskDataIntentIR ir,
                                            List<AskFilterDef> defaultFilters,
                                            List<AskMetricDef> metrics) {
        DataModel model = dataModelService.requireOwned(dataset.getDataModelId());
        DataModelSchema schema = schemaSupport.parseSchema(model.getSchemaJson());
        List<AskDimensionDef> dimensions = readList(dataset.getDimensions(), AskDimensionDef.class);
        List<String> sensitive = readStringList(dataset.getSensitiveFields());
        if (defaultFilters == null) {
            defaultFilters = List.of();
        }
        if (metrics == null) {
            metrics = List.of();
        }

        mergeMetricFilters(ir, metrics);
        if (!StringUtils.hasText(ir.getTimeGrain()) && "trend".equalsIgnoreCase(ir.getIntent())) {
            ir.setTimeGrain("day");
        }
        if (StringUtils.hasText(dataset.getDefaultTimeField())
                && "trend".equalsIgnoreCase(ir.getIntent())
                && (ir.getDimensions() == null || ir.getDimensions().isEmpty())) {
            ir.setDimensions(List.of(dataset.getDefaultTimeField()));
        }

        String intent = StringUtils.hasText(ir.getIntent()) ? ir.getIntent().toLowerCase(Locale.ROOT) : "aggregate";
        AskDataJdbcUtil.QueryRawResult raw;
        if ("lookup".equals(intent)) {
            raw = askDataJdbcUtil.lookup(model.getTableName(), schema, dimensions, ir, defaultFilters, sensitive);
        } else {
            raw = askDataJdbcUtil.aggregate(model.getTableName(), schema, dimensions, metrics, ir,
                    defaultFilters, sensitive);
        }
        return buildInsight(dataset, ir, intent, raw, metrics);
    }

    private List<AskMetricDef> upsertMetric(List<AskMetricDef> metrics, AskMetricDef preview) {
        String code = preview.getCode().trim().toLowerCase(Locale.ROOT);
        List<AskMetricDef> out = new ArrayList<>();
        boolean replaced = false;
        for (AskMetricDef m : metrics) {
            if (m.getCode() != null && code.equals(m.getCode().toLowerCase(Locale.ROOT))) {
                AskMetricDef copy = copyMetric(preview);
                copy.setCode(code);
                out.add(copy);
                replaced = true;
            } else {
                out.add(m);
            }
        }
        if (!replaced) {
            AskMetricDef copy = copyMetric(preview);
            copy.setCode(code);
            out.add(copy);
        }
        return out;
    }

    private AskMetricDef copyMetric(AskMetricDef src) {
        AskMetricDef m = new AskMetricDef();
        m.setCode(src.getCode());
        m.setName(src.getName());
        m.setDescription(src.getDescription());
        m.setOp(src.getOp());
        m.setField(src.getField());
        m.setFilters(src.getFilters() != null ? new ArrayList<>(src.getFilters()) : new ArrayList<>());
        m.setFormat(src.getFormat());
        m.setSynonyms(src.getSynonyms() != null ? new ArrayList<>(src.getSynonyms()) : new ArrayList<>());
        return m;
    }

    @Override
    public List<AskDatasetVO> searchCatalog(String keyword, Set<Long> allowedDatasetIds) {
        List<AskDatasetVO> all = askDatasetService.listAll(keyword);
        if (allowedDatasetIds == null) {
            return all;
        }
        return all.stream()
                .filter(d -> allowedDatasetIds.contains(d.getId()))
                .collect(Collectors.toList());
    }

    private AskDataResultVO buildInsight(AskDataset dataset, AskDataIntentIR ir, String intent,
                                         AskDataJdbcUtil.QueryRawResult raw, List<AskMetricDef> metrics) {
        AskDataResultVO vo = new AskDataResultVO();
        vo.setPlan(ir);
        vo.setElapsedMs(raw.elapsedMs());

        List<String> columns = new ArrayList<>();
        if (!raw.rows().isEmpty()) {
            columns.addAll(raw.rows().get(0).keySet());
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("columns", columns);
        table.put("rows", raw.rows());
        table.put("total", raw.total());
        vo.setTable(table);

        List<String> assumptions = new ArrayList<>();
        assumptions.add("数据集=" + dataset.getName() + "（" + dataset.getCode() + "）");
        if (StringUtils.hasText(dataset.getDefaultTimeField())) {
            assumptions.add("默认时间字段=" + dataset.getDefaultTimeField());
        }
        if (ir.getFilters() != null && !ir.getFilters().isEmpty()) {
            assumptions.add("过滤条件数=" + ir.getFilters().size());
        }
        vo.setAssumptions(assumptions);

        Map<String, Object> chart = new LinkedHashMap<>();
        List<String> dims = Optional.ofNullable(ir.getDimensions()).orElseGet(List::of);
        List<String> metricCodes = Optional.ofNullable(ir.getMetrics()).orElseGet(List::of);
        if ("lookup".equals(intent)) {
            chart.put("type", "table");
        } else if ("trend".equals(intent)) {
            chart.put("type", "line");
            if (!dims.isEmpty()) {
                chart.put("x", dims.get(0));
            }
            if (!metricCodes.isEmpty()) {
                chart.put("y", metricCodes.get(0));
            } else if (!columns.isEmpty()) {
                chart.put("y", columns.get(columns.size() - 1));
            }
        } else if (dims.isEmpty() && raw.rows().size() == 1) {
            chart.put("type", "kpi");
            if (!columns.isEmpty()) {
                chart.put("valueField", columns.get(columns.size() - 1));
            }
        } else {
            chart.put("type", "bar");
            if (!dims.isEmpty()) {
                chart.put("x", dims.get(0));
            }
            if (!metricCodes.isEmpty()) {
                chart.put("y", metricCodes.get(0));
            } else if (columns.size() >= 2) {
                chart.put("y", columns.get(columns.size() - 1));
            }
        }
        vo.setChart(chart);

        vo.setSummary(buildSummary(dataset, intent, raw, columns));
        vo.setFollowups(buildFollowups(intent, dims));

        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("dataset", dataset.getName());
        explain.put("datasetCode", dataset.getCode());
        explain.put("intent", intent);
        explain.put("metrics", metricCodes);
        explain.put("dimensions", dims);
        explain.put("sql", raw.sql());
        explain.put("rowCount", raw.total());
        vo.setExplain(explain);

        log.info("[AskData] 查询完成: dataset={}, intent={}, rows={}, {}ms",
                dataset.getCode(), intent, raw.rows().size(), raw.elapsedMs());
        return vo;
    }

    private String buildSummary(AskDataset dataset, String intent, AskDataJdbcUtil.QueryRawResult raw,
                                List<String> columns) {
        if (raw.rows().isEmpty()) {
            return "在「" + dataset.getName() + "」中未查询到匹配数据。可尝试放宽过滤条件或更换时间范围。";
        }
        if ("lookup".equals(intent)) {
            return "在「" + dataset.getName() + "」中查到 " + raw.total() + " 条明细，当前展示 "
                    + raw.rows().size() + " 条。";
        }
        if (raw.rows().size() == 1 && !columns.isEmpty()) {
            Object val = raw.rows().get(0).get(columns.get(columns.size() - 1));
            return "「" + dataset.getName() + "」统计结果：" + columns.get(columns.size() - 1) + " = " + val + "。";
        }
        return "「" + dataset.getName() + "」返回 " + raw.rows().size() + " 组统计结果（共 "
                + raw.total() + " 组量级）。";
    }

    private List<String> buildFollowups(String intent, List<String> dims) {
        List<String> tips = new ArrayList<>();
        if ("lookup".equals(intent)) {
            tips.add("按区域汇总统计");
            tips.add("看最近一个月趋势");
        } else if ("trend".equals(intent)) {
            tips.add("换成按周");
            tips.add("查看明细");
        } else {
            tips.add("看趋势");
            tips.add("查看明细");
            if (dims.isEmpty()) {
                tips.add("按维度分组");
            }
        }
        return tips;
    }

    private void mergeMetricFilters(AskDataIntentIR ir, List<AskMetricDef> metrics) {
        if (ir.getMetrics() == null || ir.getMetrics().isEmpty() || metrics == null) {
            return;
        }
        Map<String, AskMetricDef> byCode = new HashMap<>();
        for (AskMetricDef m : metrics) {
            if (m.getCode() != null) {
                byCode.put(m.getCode().toLowerCase(Locale.ROOT), m);
            }
        }
        List<AskDataIntentIR.AskDataFilter> filters = ir.getFilters() != null
                ? new ArrayList<>(ir.getFilters()) : new ArrayList<>();
        for (String code : ir.getMetrics()) {
            AskMetricDef m = byCode.get(code.toLowerCase(Locale.ROOT));
            if (m == null || m.getFilters() == null) {
                continue;
            }
            for (AskFilterDef df : m.getFilters()) {
                if (df == null || !StringUtils.hasText(df.getField())) {
                    continue;
                }
                AskDataIntentIR.AskDataFilter f = new AskDataIntentIR.AskDataFilter();
                f.setDim(df.getField());
                f.setField(df.getField());
                f.setOp(StringUtils.hasText(df.getOp()) ? df.getOp() : "eq");
                f.setValue(df.getValue());
                filters.add(f);
            }
        }
        ir.setFilters(filters);
    }

    private List<AskFilterDef> readFilterList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AskFilterDef>>() {});
        } catch (Exception ignored) {
            // fall through
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
