package com.lightbot.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统工具标记注解
 * <p>可标记在类或方法上。标记在类上表示该类所有 @Tool 方法为内置工具；
 * 标记在方法上可覆盖类级别的 displayName、autoInject 配置。</p>
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
     * 是否自动注入所有 Agent
     * <p>true: 系统工具，自动注入，不可编辑删除
     * false: 普通内置工具，用户可选择绑定</p>
     */
    boolean autoInject() default false;
}