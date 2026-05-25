package com.lightbot.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统工具标记注解
 * 平台系统工具：自动注入所有 Agent（isSystem=true），由 {@link com.lightbot.tool.registrar.PlatformSystemToolRegistrar} 注册。
 * 内置工具：用户可选绑定（isSystem=false），由 {@link com.lightbot.tool.registrar.BuiltinToolRegistrar} 注册。
 *
 * <p>displayName 优先级：方法级别 > 类级别</p>
 * <p>autoInject 优先级：方法级别 > 类级别（默认 false）</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface SystemTool {

    /**
     * 工具显示名称（中文）
     */
    String displayName();

    /**
     * 工具描述
     */
    String description() default "";

    /**
     * 是否作为平台系统工具自动注入所有 Agent
     * <p>true: 平台系统工具（仅 systemtool 包），isSystem=true，不可编辑删除
     * false: 普通内置工具（builtin 包），isSystem=false，用户可选择绑定</p>
     */
    boolean autoInject() default false;
}