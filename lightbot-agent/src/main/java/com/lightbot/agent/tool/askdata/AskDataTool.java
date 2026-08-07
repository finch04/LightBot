package com.lightbot.agent.tool.askdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.askdata.AskDataIntentIR;
import com.lightbot.service.AgentService;
import com.lightbot.service.AskDataQueryService;
import com.lightbot.service.AskDatasetService;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import com.lightbot.vo.AskDataResultVO;
import com.lightbot.vo.AskDatasetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能问数工具（Agent 侧，需读取 agent.config.datasets 白名单）
 *
 * @author finch
 * @since 2026-07-30
 */
@Slf4j
@Component("askDataTool")
@RequiredArgsConstructor
@SystemTool(displayName = "智能问数", icon = "BarChartOutlined",
        description = "基于企业数据池语义层进行查数、统计、趋势分析", tags = {"问数", "数据分析"})
public class AskDataTool {

    public static final String TOOL_SEARCH = "ask_data_search_catalog";
    public static final String TOOL_DESCRIBE = "ask_data_describe_dataset";
    public static final String TOOL_EXECUTE = "ask_data_execute";

    private final AskDataQueryService askDataQueryService;
    private final AskDatasetService askDatasetService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    @SystemTool(displayName = "检索问数目录", tags = {"问数"})
    @Tool(name = TOOL_SEARCH,
            description = "检索当前智能体可问的数据集与指标目录。用户问数前先调用以锁定 dataset code 与 metric code。"
                    + "可选 keyword 过滤名称/编码/描述。")
    public String searchCatalog(
            @ToolParam(description = "可选关键词，如客户、订单、活跃")
            @ToolParamMeta(example = "客户") String keyword,
            ToolContext toolContext) {
        try {
            Set<Long> allowed = resolveAllowed(toolContext);
            if (allowed.isEmpty()) {
                return "{\"error\":\"当前智能体未绑定可问数据模型，请先在 Agent 详情「可问数据」中勾选数据模型\"}";
            }
            List<AskDatasetVO> list = askDataQueryService.searchCatalog(keyword, allowed);
            List<Map<String, Object>> items = list.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", String.valueOf(d.getId()));
                m.put("code", d.getCode());
                m.put("name", d.getName());
                m.put("description", d.getDescription());
                m.put("defaultTimeField", d.getDefaultTimeField());
                m.put("metrics", d.getMetrics());
                m.put("dimensions", d.getDimensions());
                m.put("modelFields", d.getModelFields());
                m.put("dataModelName", d.getDataModelName());
                return m;
            }).toList();
            return objectMapper.writeValueAsString(Map.of("datasets", items, "total", items.size()));
        } catch (Exception e) {
            log.warn("[Tool:ask_data_search_catalog] {}", e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    @SystemTool(displayName = "描述问数数据集", tags = {"问数"})
    @Tool(name = TOOL_DESCRIBE,
            description = "查看指定问数数据集的完整语义：维度、指标、默认过滤、字段画像。dataset 传 code 或 id。")
    public String describeDataset(
            @ToolParam(description = "数据集 code 或 id")
            @ToolParamMeta(example = "customer") String dataset,
            ToolContext toolContext) {
        try {
            Set<Long> allowed = resolveAllowed(toolContext);
            AskDatasetVO vo = dataset != null && dataset.matches("^\\d+$")
                    ? askDatasetService.getDetail(Long.parseLong(dataset))
                    : askDatasetService.getByCode(dataset);
            if (!allowed.contains(vo.getId())) {
                return "{\"error\":\"无权访问该数据集\"}";
            }
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            log.warn("[Tool:ask_data_describe_dataset] {}", e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    @SystemTool(displayName = "执行问数查询", tags = {"问数"})
    @Tool(name = TOOL_EXECUTE,
            description = "执行智能问数 Intent IR（JSON 字符串）。"
                    + "必填 dataset(code)；intent=lookup|aggregate|trend|rank|distribute；"
                    + "metrics 为指标 code 列表（可用 count 或 sum:amount）；"
                    + "dimensions 为分组字段；filters 为 [{dim,op,value}]；"
                    + "op 支持 eq/ne/in/not_in/like/not_like/starts_with/not_starts_with/gt/gte/lt/lte/between/in_last。"
                    + "先 search_catalog / describe，再构造 IR。返回结论+表+图建议+SQL说明。")
    public String execute(
            @ToolParam(description = "Intent IR JSON")
            @ToolParamMeta(example = "{\"dataset\":\"customer\",\"intent\":\"aggregate\",\"metrics\":[\"count\"]}")
            String intentJson,
            ToolContext toolContext) {
        try {
            if (!StringUtils.hasText(intentJson)) {
                return "{\"error\":\"intentJson 不能为空\"}";
            }
            AskDataIntentIR ir = objectMapper.readValue(intentJson, AskDataIntentIR.class);
            Set<Long> allowed = resolveAllowed(toolContext);
            if (allowed.isEmpty()) {
                return "{\"error\":\"当前智能体未绑定可问数据模型\"}";
            }
            AskDataResultVO result = askDataQueryService.execute(ir, allowed, resolveTenantValues(toolContext));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[Tool:ask_data_execute] {}", e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * 从 ToolContext 提取租户隔离主键（扁平键或 callerContext 对象）
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolveTenantValues(ToolContext toolContext) {
        Map<String, String> values = new LinkedHashMap<>();
        if (toolContext == null || toolContext.getContext() == null) {
            return values;
        }
        Map<String, Object> ctx = toolContext.getContext();
        putTenantValue(values, "regionId", ctx.get("regionId"));
        putTenantValue(values, "enterpriseId", ctx.get("enterpriseId"));
        Object caller = ctx.get("callerContext");
        if (caller instanceof Map<?, ?> callerMap) {
            putTenantValue(values, "regionId", callerMap.get("regionId"));
            putTenantValue(values, "enterpriseId", callerMap.get("enterpriseId"));
        }
        return values;
    }

    private static void putTenantValue(Map<String, String> values, String key, Object raw) {
        if (values.containsKey(key) && StringUtils.hasText(values.get(key))) {
            return;
        }
        if (raw != null && StringUtils.hasText(String.valueOf(raw))) {
            values.put(key, String.valueOf(raw).trim());
        }
    }

    /**
     * 解析可问 Dataset ID：dataModels 自动 ensure + 兼容旧 datasets 绑定
     */
    private Set<Long> resolveAllowed(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return Set.of();
        }
        Object agentIdObj = toolContext.getContext().get("agentId");
        if (agentIdObj == null) {
            return Set.of();
        }
        Long agentId = Long.parseLong(String.valueOf(agentIdObj));
        Set<Long> allowed = new LinkedHashSet<>();
        List<Long> modelIds = agentService.getDataModelIds(agentId);
        if (modelIds != null) {
            for (Long modelId : modelIds) {
                AskDatasetVO ds = askDatasetService.ensureFromModel(modelId);
                if (ds != null && ds.getId() != null) {
                    allowed.add(ds.getId());
                }
            }
        }
        List<Long> legacy = agentService.getDatasetIds(agentId);
        if (legacy != null) {
            allowed.addAll(legacy);
        }
        return allowed;
    }

    private String errorJson(String msg) {
        String safe = msg == null ? "" : msg.replace("\"", "'").replace("\n", " ");
        return "{\"error\":\"" + safe + "\"}";
    }
}
