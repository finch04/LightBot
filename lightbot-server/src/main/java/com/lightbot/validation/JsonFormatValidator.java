package com.lightbot.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 格式校验器
 * <p>校验字符串是否为合法的 JSON 对象或数组格式</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
public class JsonFormatValidator implements ConstraintValidator<JsonFormat, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 为空时跳过校验（配合 @NotNull 使用）
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            // 校验是否为合法 JSON
            Object parsed = OBJECT_MAPPER.readValue(value, Object.class);
            // 只允许 JSON 对象或数组，不允许纯字符串/数字
            return parsed instanceof java.util.Map || parsed instanceof java.util.List;
        } catch (Exception e) {
            log.debug("[JsonFormatValidator] JSON 格式校验失败: {}", e.getMessage());
            return false;
        }
    }
}
