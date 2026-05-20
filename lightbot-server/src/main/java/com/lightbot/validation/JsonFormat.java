package com.lightbot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * JSON 格式校验注解
 * <p>校验字符串是否为合法的 JSON 格式</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = JsonFormatValidator.class)
public @interface JsonFormat {

    String message() default "必须为合法的JSON格式";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
