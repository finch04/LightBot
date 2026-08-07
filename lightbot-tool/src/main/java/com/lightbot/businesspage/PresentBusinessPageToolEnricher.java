package com.lightbot.businesspage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbot.service.BusinessPageService;
import com.lightbot.tool.builtin.PresentBusinessPageTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将会话可用的业务页 catalog 注入 present_business_page 工具描述与 pageType enum，
 * 强制模型只能从注册列表精确选择，禁止自造同义 pageType。
 *
 * @author finch
 * @since 2026-08-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresentBusinessPageToolEnricher {

    private final BusinessPageService businessPageService;
    private final ObjectMapper objectMapper;

    /**
     * 包装工具列表中的 present_business_page（若存在）。
     *
     * @param callbacks        当前会话工具回调
     * @param allowedPageTypes 本会话允许的 pageType（已做 API Key ∩ Agent 交集）
     * @return 包装后的列表（可能为新 List）
     */
    public List<ToolCallback> enrich(List<ToolCallback> callbacks, Collection<String> allowedPageTypes) {
        if (callbacks == null || callbacks.isEmpty()) {
            return callbacks;
        }
        Set<String> allowed = normalizeAllowed(allowedPageTypes);
        List<ToolCallback> out = new ArrayList<>(callbacks.size());
        boolean wrapped = false;
        for (ToolCallback cb : callbacks) {
            if (cb != null && PresentBusinessPageTool.TOOL_NAME.equals(cb.getToolDefinition().name())) {
                out.add(wrap(cb, allowed));
                wrapped = true;
            } else {
                out.add(cb);
            }
        }
        if (wrapped) {
            log.debug("[BusinessPage] 已注入 pageType catalog，允许 {} 个", allowed.size());
        }
        return out;
    }

    private ToolCallback wrap(ToolCallback original, Set<String> allowed) {
        String catalog = buildCatalog(allowed);
        String description = buildDescription(original.getToolDefinition().description(), catalog, allowed.isEmpty());
        String inputSchema = patchPageTypeEnum(original.getToolDefinition().inputSchema(), allowed);
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(original.getToolDefinition().name())
                .description(description)
                .inputSchema(inputSchema)
                .build();
        return new CatalogAwareCallback(original, definition);
    }

    private String buildDescription(String base, String catalog, boolean empty) {
        String prefix = base == null ? "" : base.trim();
        if (empty) {
            return prefix + " 【强制】当前会话没有任何可用业务页，禁止调用本工具。";
        }
        return prefix
                + " 【强制】pageType 必须从下列列表中精确复制（禁止自造同义名，如 utility_payment 不等于 utility_bill_pay）："
                + catalog
                + "。无匹配项时向用户说明暂不支持该业务，不要猜测 pageType。";
    }

    private String buildCatalog(Set<String> allowed) {
        return allowed.stream()
                .map(type -> businessPageService.resolveEnabled(type)
                        .map(d -> d.pageType() + "=" + d.displayName())
                        .orElse(type))
                .collect(Collectors.joining("；"));
    }

    private String patchPageTypeEnum(String inputSchema, Set<String> allowed) {
        if (inputSchema == null || inputSchema.isBlank() || allowed.isEmpty()) {
            return inputSchema;
        }
        try {
            JsonNode root = objectMapper.readTree(inputSchema);
            if (!(root instanceof ObjectNode rootObj)) {
                return inputSchema;
            }
            JsonNode props = rootObj.get("properties");
            if (!(props instanceof ObjectNode propsObj)) {
                return inputSchema;
            }
            JsonNode pageTypeNode = propsObj.get("pageType");
            ObjectNode pageTypeObj;
            if (pageTypeNode instanceof ObjectNode existing) {
                pageTypeObj = existing;
            } else {
                pageTypeObj = objectMapper.createObjectNode();
                pageTypeObj.put("type", "string");
                propsObj.set("pageType", pageTypeObj);
            }
            ArrayNode enumNode = objectMapper.createArrayNode();
            for (String type : allowed) {
                enumNode.add(type);
            }
            pageTypeObj.set("enum", enumNode);
            pageTypeObj.put("description",
                    "必须从枚举中精确选择已注册 pageType，禁止自造别名。可用：" + buildCatalog(allowed));
            return objectMapper.writeValueAsString(rootObj);
        } catch (Exception e) {
            log.warn("[BusinessPage] 注入 pageType enum 失败，仅保留描述约束: {}", e.getMessage());
            return inputSchema;
        }
    }

    private Set<String> normalizeAllowed(Collection<String> allowedPageTypes) {
        if (allowedPageTypes == null || allowedPageTypes.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String type : allowedPageTypes) {
            if (type != null && !type.isBlank()) {
                out.add(type.trim());
            }
        }
        return out;
    }

    private static final class CatalogAwareCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final ToolDefinition definition;

        private CatalogAwareCallback(ToolCallback delegate, ToolDefinition definition) {
            this.delegate = delegate;
            this.definition = definition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return toolContext != null
                    ? delegate.call(toolInput, toolContext)
                    : delegate.call(toolInput);
        }
    }
}
