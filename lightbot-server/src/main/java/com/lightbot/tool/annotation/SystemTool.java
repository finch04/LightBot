package com.lightbot.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具标记注解
 * <p>标记工具的显示名称、描述、类型和标签，由 {@link com.lightbot.tool.registrar.ToolRegistrar} 统一注册。</p>
 *
 * <p>displayName 优先级：方法级别 > 类级别</p>
 * <p>type 优先级：方法级别 > 类级别（默认 "builtin"）</p>
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
     * 工具类型（对应 ToolType 枚举的 code）
     * <p>"builtin": 通用内置工具，用户可选绑定
     * "knowledge": 知识库工具，Agent 绑定知识库时自动注入</p>
     */
    String type() default "";

    /**
     * 工具标签（用于分类筛选）
     * <p>如 {"知识库", "交互", "计算", "搜索", "图片", "数据库"}</p>
     */
    String[] tags() default {};

    /**
     * 输出示例 JSON（用于前端展示工具返回结构）
     */
    String outputExample() default "";

    /**
     * 输出参数 JSON Schema（定义工具返回的结构）
     * <p>如 {"type":"object","properties":{"total":{"type":"integer","description":"结果总数"}}}</p>
     */
    String outputSchema() default "";
}
