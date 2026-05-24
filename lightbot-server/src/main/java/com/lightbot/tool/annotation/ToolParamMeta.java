package com.lightbot.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数元数据注解
 * <p>配合 Spring AI 的 @ToolParam 使用，提供额外的参数信息如示例值。</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParamMeta {

    /**
     * 参数示例值
     */
    String example() default "";

    /**
     * 是否必填（默认true）
     */
    boolean required() default true;
}