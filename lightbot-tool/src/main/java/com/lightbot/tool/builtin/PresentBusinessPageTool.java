package com.lightbot.tool.builtin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.businesspage.BusinessPageDefinition;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.dto.CallerContext;
import com.lightbot.service.BusinessPageService;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置工具 — 在对话中呈现已由开发者注册的业务办理页。
 * <p>调用方身份仅从 {@link ToolContext} 写入结果，禁止从模型 props 推断。</p>
 *
 * @author finch
 * @since 2026-08-04
 */
@Slf4j
@Component("presentBusinessPageTool")
@RequiredArgsConstructor
@SystemTool(
        displayName = "业务办理页",
        icon = "AppstoreOutlined",
        description = "在对话中呈现已在能力中心注册的业务办理页（H5 HTML / 外链）",
        tags = {"交互", "业务页"},
        outputExample = "{\"success\":true,\"pageType\":\"leave_request\",\"title\":\"请假申请\",\"mode\":\"inline\",\"wait_for_user\":true,\"props\":{\"days\":1},\"callerContext\":{\"externalUserId\":\"u1\",\"regionId\":\"510100\"},\"identityHeaders\":{\"X-Zhiyuan-Region-Id\":\"510100\"},\"pageHtml\":\"<!DOCTYPE html>...\",\"actions\":[\"submit\",\"cancel\"]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"success\":{\"type\":\"boolean\"},\"pageType\":{\"type\":\"string\"},\"wait_for_user\":{\"type\":\"boolean\"},\"props\":{\"type\":\"object\"},\"callerContext\":{\"type\":\"object\"},\"identityHeaders\":{\"type\":\"object\"},\"pageHtml\":{\"type\":\"string\"},\"pageUrl\":{\"type\":\"string\"}}}"
)
public class PresentBusinessPageTool {

    public static final String TOOL_NAME = "present_business_page";

    /** 平台默认身份 Header（业务后端优先认） */
    public static final String HEADER_EXTERNAL_USER_ID = "X-Zhiyuan-External-User-Id";
    public static final String HEADER_REGION_ID = "X-Zhiyuan-Region-Id";
    public static final String HEADER_ENTERPRISE_ID = "X-Zhiyuan-Enterprise-Id";

    /** 页面级身份透传配置键（来自 defaultOptions，模型不可覆盖） */
    private static final Set<String> IDENTITY_OPTION_KEYS = Set.of(
            "injectIdentityHeaders",
            "contextHeaders",
            "contextHeaderUrlIncludes",
            "exposeProfile"
    );

    private static final Pattern CONTEXT_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    private final BusinessPageService businessPageService;
    private final ObjectMapper objectMapper;

    @Tool(name = TOOL_NAME,
            description = "当用户要办理具体业务时，在对话气泡内呈现已注册的业务办理页。"
                    + "【强制】pageType 只能使用工具描述末尾「可用列表」或参数 enum 中的值，必须精确复制，禁止自造同义名（例如不要用 utility_payment 代替 utility_bill_pay）。"
                    + "不要编造页面结构或 HTML。"
                    + "展示模式默认且优先使用 inline（嵌在对话消息内）；除非用户明确要求侧栏/抽屉，否则不要传 drawer。"
                    + "props 传 JSON 对象字符串（字段需在注册白名单内）；options 可传少量文案定制。"
                    + "【强制】调用成功后不要再输出操作指南、步骤列表或复述页面字段；页面已在对话中展示，等待用户在页面内提交/取消即可。"
                    + "若无匹配业务页，直接告知用户暂不支持，不要猜测 pageType。")
    public String presentBusinessPage(
            @ToolParam(description = "必须从可用列表/enum 中精确选择的 pageType，禁止自造别名")
            @ToolParamMeta(example = "utility_bill_pay") String pageType,
            @ToolParam(description = "页面标题，可选", required = false)
            @ToolParamMeta(example = "请假申请", required = false) String title,
            @ToolParam(description = "业务数据 JSON 对象字符串（预填到 H5 页的 props）", required = false)
            @ToolParamMeta(example = "{\"days\":1,\"reason\":\"事假\"}", required = false) String props,
            @ToolParam(description = "展示模式：默认 inline（对话内嵌）。仅当用户明确要求侧栏时用 drawer", required = false)
            @ToolParamMeta(example = "inline", required = false) String mode,
            @ToolParam(description = "允许操作，逗号分隔或 JSON 数组", required = false)
            @ToolParamMeta(example = "submit,cancel", required = false) String actions,
            @ToolParam(description = "文案/显隐定制 JSON", required = false)
            @ToolParamMeta(example = "{\"primaryButtonText\":\"提交申请\",\"hint\":\"请填写请假信息\"}", required = false) String options,
            ToolContext toolContext) {

        log.info("[Tool:present_business_page] pageType={}, mode={}", pageType, mode);

        String catalog = resolveSessionCatalog(toolContext);
        BusinessPageDefinition definition = businessPageService.resolveEnabled(pageType).orElse(null);
        if (definition == null) {
            return ToolResultPrefixes.failureJson(
                    "未知或已禁用 pageType: " + pageType
                            + "。禁止使用未注册别名；请从下列列表精确选择：" + catalog);
        }

        @SuppressWarnings("unchecked")
        Collection<String> allowed = toolContext != null && toolContext.getContext() != null
                ? (Collection<String>) toolContext.getContext().get("allowedBusinessPages")
                : null;
        if (allowed != null && !allowed.contains(definition.pageType())) {
            return ToolResultPrefixes.failureJson(
                    "当前会话无权呈现业务页: " + definition.pageType()
                            + "。允许：" + (allowed.isEmpty() ? "（无）" : String.join(",", allowed)));
        }

        Map<String, Object> mergedProps = mergeAllowedMap(definition.defaultProps(), parseObject(props), definition.allowedPropKeys());
        Map<String, Object> mergedOptions = mergePageOptions(definition, parseObject(options));
        String resolvedMode = resolveMode(mode, definition);
        List<String> resolvedActions = resolveActions(actions, definition);
        String resolvedTitle = (title != null && !title.isBlank()) ? title.trim() : definition.defaultTitle();

        // 身份仅从 ToolContext 断言；固化到工具结果供历史重放
        Map<String, Object> callerContext = resolveCallerContextSnapshot(toolContext, mergedOptions);
        Map<String, String> identityHeaders = buildIdentityHeaders(callerContext, mergedOptions);

        ToolEventEmitter.emit("正在打开业务办理页: " + definition.displayName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageType", definition.pageType());
        result.put("displayName", definition.displayName());
        result.put("title", resolvedTitle);
        result.put("mode", resolvedMode);
        result.put("props", mergedProps);
        result.put("actions", resolvedActions);
        result.put("options", mergedOptions);
        result.put("callerContext", callerContext);
        result.put("identityHeaders", identityHeaders);
        Map<String, Object> identityMeta = new LinkedHashMap<>();
        Map<String, String> headerNames = new LinkedHashMap<>();
        headerNames.put("externalUserId", HEADER_EXTERNAL_USER_ID);
        headerNames.put("regionId", HEADER_REGION_ID);
        headerNames.put("enterpriseId", HEADER_ENTERPRISE_ID);
        identityMeta.put("headerNames", headerNames);
        result.put("identity", identityMeta);
        if (definition.pageHtml() != null && !definition.pageHtml().isBlank()) {
            result.put("pageHtml", definition.pageHtml());
            result.put("renderHint", "h5");
        } else if (definition.pageUrl() != null && !definition.pageUrl().isBlank()) {
            result.put("pageUrl", definition.pageUrl());
            result.put("renderHint", "h5");
        } else {
            result.put("renderHint", "fallback");
        }
        // 等待用户在页面提交/取消（对话回灌 + Workflow HITL）
        result.put("wait_for_user", true);
        result.put("break_loop", true);
        result.put("schemaVersion", 2);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return ToolResultPrefixes.failureJson("序列化失败: " + e.getMessage());
        }
    }

    /**
     * 合并 options：页面 defaultOptions 为底；模型入参受白名单约束；身份键始终以页面注册为准。
     */
    private Map<String, Object> mergePageOptions(BusinessPageDefinition definition, Map<String, Object> incoming) {
        Map<String, Object> merged = mergeAllowedMap(
                definition.defaultOptions(),
                incoming,
                definition.allowedOptionKeys());
        // 身份透传配置不可被模型覆盖
        Map<String, Object> defaults = definition.defaultOptions() != null ? definition.defaultOptions() : Map.of();
        for (String key : IDENTITY_OPTION_KEYS) {
            if (defaults.containsKey(key)) {
                merged.put(key, defaults.get(key));
            }
        }
        // 平台默认：出站注 Header、暴露 profile（注册未写时补齐，便于前端/桥接）
        if (!merged.containsKey("injectIdentityHeaders")) {
            merged.put("injectIdentityHeaders", true);
        }
        if (!merged.containsKey("exposeProfile")) {
            merged.put("exposeProfile", true);
        }
        return merged;
    }

    /**
     * 从 ToolContext 组装可序列化身份快照（禁止读模型 props）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveCallerContextSnapshot(ToolContext toolContext, Map<String, Object> options) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        Map<String, Object> raw = null;
        Object caller = ctx.get("callerContext");
        if (caller instanceof Map<?, ?> map && !map.isEmpty()) {
            raw = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    raw.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } else {
            // 兼容仅扁平键
            CallerContext built = new CallerContext();
            Object eu = ctx.get("externalUserId");
            if (eu != null && !String.valueOf(eu).isBlank()) {
                built.setExternalUserId(String.valueOf(eu).trim());
            }
            Object region = ctx.get("regionId");
            if (region != null && !String.valueOf(region).isBlank()) {
                built.setRegionId(String.valueOf(region).trim());
            }
            Object enterprise = ctx.get("enterpriseId");
            if (enterprise != null && !String.valueOf(enterprise).isBlank()) {
                built.setEnterpriseId(String.valueOf(enterprise).trim());
            }
            if (!built.isEmpty()) {
                raw = built.toMap();
            }
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        boolean exposeProfile = options.get("exposeProfile") != Boolean.FALSE;
        if (!exposeProfile) {
            raw.remove("profile");
        }
        return raw.isEmpty() ? null : raw;
    }

    /**
     * 渲染出站身份 Header：有 contextHeaders 模板则用之，否则平台默认三件套。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> buildIdentityHeaders(Map<String, Object> callerContext, Map<String, Object> options) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (callerContext == null || callerContext.isEmpty()) {
            return headers;
        }
        if (options.get("injectIdentityHeaders") == Boolean.FALSE) {
            return headers;
        }
        Object custom = options.get("contextHeaders");
        if (custom instanceof Map<?, ?> mapping && !mapping.isEmpty()) {
            Map<String, Object> vars = flattenCallerVars(callerContext);
            for (Map.Entry<?, ?> e : mapping.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String rendered = renderContextTemplate(String.valueOf(e.getValue()), vars);
                if (rendered != null && !rendered.isBlank()) {
                    headers.put(String.valueOf(e.getKey()), rendered);
                }
            }
            return headers;
        }
        putHeaderIfPresent(headers, HEADER_EXTERNAL_USER_ID, callerContext.get("externalUserId"));
        putHeaderIfPresent(headers, HEADER_REGION_ID, callerContext.get("regionId"));
        putHeaderIfPresent(headers, HEADER_ENTERPRISE_ID, callerContext.get("enterpriseId"));
        return headers;
    }

    private static void putHeaderIfPresent(Map<String, String> headers, String name, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            headers.put(name, String.valueOf(value).trim());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenCallerVars(Map<String, Object> callerContext) {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : callerContext.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            vars.put(e.getKey(), e.getValue());
            vars.put("callerContext." + e.getKey(), e.getValue());
            if ("profile".equals(e.getKey()) && e.getValue() instanceof Map<?, ?> profile) {
                for (Map.Entry<?, ?> pe : profile.entrySet()) {
                    if (pe.getKey() != null && pe.getValue() != null) {
                        vars.put("callerContext.profile." + pe.getKey(), pe.getValue());
                        vars.put("profile." + pe.getKey(), pe.getValue());
                    }
                }
            }
        }
        return vars;
    }

    private String renderContextTemplate(String template, Map<String, Object> vars) {
        if (template == null) {
            return null;
        }
        Matcher matcher = CONTEXT_PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1);
            Object value = vars.get(path);
            String replacement = value == null ? "" : Matcher.quoteReplacement(String.valueOf(value));
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 优先使用会话白名单 catalog，否则回落全量启用列表。
     */
    @SuppressWarnings("unchecked")
    private String resolveSessionCatalog(ToolContext toolContext) {
        Collection<String> allowed = toolContext != null && toolContext.getContext() != null
                ? (Collection<String>) toolContext.getContext().get("allowedBusinessPages")
                : null;
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return "（无）";
            }
            return allowed.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .map(type -> businessPageService.resolveEnabled(type)
                            .map(d -> d.pageType() + "=" + d.displayName())
                            .orElse(type))
                    .collect(java.util.stream.Collectors.joining("；"));
        }
        return businessPageService.catalogForToolDescription();
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object raw = objectMapper.readValue(json.trim(), Object.class);
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("[Tool:present_business_page] JSON 解析失败: {}", e.getMessage());
        }
        return Map.of();
    }

    private Map<String, Object> mergeAllowedMap(
            Map<String, Object> defaults,
            Map<String, Object> incoming,
            Set<String> allowedKeys) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults != null ? defaults : Map.of());
        if (incoming == null || incoming.isEmpty()) {
            return merged;
        }
        // 未配置白名单时放行全部（H5 页常见）
        if (allowedKeys == null || allowedKeys.isEmpty()) {
            for (Map.Entry<String, Object> e : incoming.entrySet()) {
                if (e.getValue() != null && !IDENTITY_OPTION_KEYS.contains(e.getKey())) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
            return merged;
        }
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            if (IDENTITY_OPTION_KEYS.contains(e.getKey())) {
                continue;
            }
            if (allowedKeys.contains(e.getKey()) && e.getValue() != null) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return merged;
    }

    private String resolveMode(String mode, BusinessPageDefinition definition) {
        // 产品默认：对话内嵌；未传或非法值一律回落 inline（若注册表允许）
        String candidate = mode == null || mode.isBlank() ? "inline" : mode.trim().toLowerCase();
        if (!"inline".equals(candidate) && !"drawer".equals(candidate)) {
            candidate = "inline";
        }
        if (definition.allowedModes().contains(candidate)) {
            return candidate;
        }
        if (definition.allowedModes().contains("inline")) {
            return "inline";
        }
        return definition.allowedModes().iterator().next();
    }

    private List<String> resolveActions(String actions, BusinessPageDefinition definition) {
        Set<String> requested = new LinkedHashSet<>();
        if (actions != null && !actions.isBlank()) {
            String trimmed = actions.trim();
            if (trimmed.startsWith("[")) {
                try {
                    List<String> list = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                    if (list != null) {
                        for (String item : list) {
                            if (item != null && !item.isBlank()) {
                                requested.add(item.trim());
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (requested.isEmpty()) {
                for (String part : trimmed.split(",")) {
                    if (part != null && !part.isBlank()) {
                        requested.add(part.trim());
                    }
                }
            }
        }
        List<String> resolved = new ArrayList<>();
        if (requested.isEmpty()) {
            resolved.addAll(definition.allowedActions());
            return resolved;
        }
        for (String action : requested) {
            if (definition.allowedActions().contains(action)) {
                resolved.add(action);
            }
        }
        return resolved.isEmpty() ? new ArrayList<>(definition.allowedActions()) : resolved;
    }
}
