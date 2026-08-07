package com.lightbot.businesspage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务办理页模板定义（开发者注册）。
 * <p>主路径为 {@code pageHtml}（直接登记 H5，iframe srcdoc）；其次 {@code pageUrl} 外链。</p>
 *
 * @param pageType           稳定页面类型码
 * @param displayName        展示名称
 * @param description        用途说明（给建设者 / LLM）
 * @param defaultTitle       默认标题
 * @param pageHtml           直接登记的 H5 HTML
 * @param pageUrl            可选外链 H5
 * @param allowedModes       允许的展示模式
 * @param allowedActions     允许的操作
 * @param allowedPropKeys    props 白名单（空则放行全部）
 * @param allowedOptionKeys  options 白名单
 * @param defaultProps       默认 props
 * @param defaultOptions     默认 options（身份透传等）
 * @param formSchema         已废弃，恒为 null
 * @param builtin            兼容字段
 * @author finch
 * @since 2026-08-04
 */
public record BusinessPageDefinition(
        String pageType,
        String displayName,
        String description,
        String defaultTitle,
        String pageHtml,
        String pageUrl,
        Set<String> allowedModes,
        Set<String> allowedActions,
        Set<String> allowedPropKeys,
        Set<String> allowedOptionKeys,
        Map<String, Object> defaultProps,
        Map<String, Object> defaultOptions,
        Map<String, Object> formSchema,
        boolean builtin
) {
    public BusinessPageDefinition {
        pageHtml = pageHtml == null || pageHtml.isBlank() ? null : pageHtml;
        pageUrl = pageUrl == null || pageUrl.isBlank() ? null : pageUrl.trim();
        allowedModes = allowedModes == null ? Set.of("inline") : Collections.unmodifiableSet(new LinkedHashSet<>(allowedModes));
        allowedActions = allowedActions == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(allowedActions));
        allowedPropKeys = allowedPropKeys == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(allowedPropKeys));
        allowedOptionKeys = allowedOptionKeys == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(allowedOptionKeys));
        defaultProps = defaultProps == null ? Map.of() : Collections.unmodifiableMap(defaultProps);
        defaultOptions = defaultOptions == null ? Map.of() : Collections.unmodifiableMap(defaultOptions);
        formSchema = formSchema == null || formSchema.isEmpty() ? null : Collections.unmodifiableMap(formSchema);
    }

    public static BusinessPageDefinition of(
            String pageType,
            String displayName,
            String description,
            String defaultTitle,
            String pageHtml,
            String pageUrl,
            List<String> modes,
            List<String> actions,
            List<String> propKeys,
            List<String> optionKeys,
            Map<String, Object> defaultProps,
            Map<String, Object> defaultOptions,
            Map<String, Object> formSchema,
            boolean builtin) {
        return new BusinessPageDefinition(
                pageType,
                displayName,
                description,
                defaultTitle,
                pageHtml,
                pageUrl,
                new LinkedHashSet<>(modes == null ? List.of("inline") : modes),
                new LinkedHashSet<>(actions == null ? List.of() : actions),
                new LinkedHashSet<>(propKeys == null ? List.of() : propKeys),
                new LinkedHashSet<>(optionKeys == null ? List.of() : optionKeys),
                defaultProps,
                defaultOptions,
                formSchema,
                builtin
        );
    }
}
