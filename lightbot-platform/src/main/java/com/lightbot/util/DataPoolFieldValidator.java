package com.lightbot.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.enums.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据池字段值校验：按组件类型校验格式与可选范围
 *
 * @author finch
 * @since 2026-07-26
 */
@Component
public class DataPoolFieldValidator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public DataPoolFieldValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验单条记录全部业务字段
     *
     * @param fields    业务字段定义
     * @param data      提交数据
     * @param forInsert 是否新增（必填更严）
     */
    public void validateRecord(List<DataModelSchema.FieldDef> fields, Map<String, Object> data, boolean forInsert) {
        Map<String, Object> payload = data != null ? data : Map.of();
        for (DataModelSchema.FieldDef field : fields) {
            validateField(field, payload.get(field.getKey()), forInsert);
        }
    }

    /**
     * 按字段类型校验单个值
     */
    public void validateField(DataModelSchema.FieldDef field, Object value, boolean forInsert) {
        String label = StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey();
        boolean empty = isEmpty(value);
        if (empty) {
            if (forInsert && Boolean.TRUE.equals(field.getRequired())) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 不能为空");
            }
            return;
        }
        String type = field.getType() != null ? field.getType().toLowerCase(Locale.ROOT) : "input";
        switch (type) {
            case "number" -> validateNumber(label, value);
            case "date" -> validateDate(label, value);
            case "datetime" -> validateDateTime(label, value);
            case "select", "radio" -> validateSingleOption(label, value, optionValues(field));
            case "checkbox" -> validateMultiOptions(label, value, optionValues(field));
            case "upload" -> validateUpload(label, value);
            default -> {
                // input / textarea：仅做非空与字符串化
                if (String.valueOf(value).length() > 10000) {
                    throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 内容过长");
                }
            }
        }
    }

    private void validateNumber(String label, Object value) {
        try {
            if (value instanceof Number) {
                return;
            }
            new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 必须是数字");
        }
    }

    private void validateDate(String label, Object value) {
        String s = String.valueOf(value).trim();
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 格式应为 yyyy-MM-dd");
        }
    }

    private void validateDateTime(String label, Object value) {
        String s = String.valueOf(value).trim().replace('T', ' ');
        if (s.length() == 10) {
            s = s + " 00:00:00";
        }
        if (s.length() >= 19) {
            s = s.substring(0, 19);
        }
        try {
            LocalDateTime.parse(s, DATE_TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private void validateSingleOption(String label, Object value, Set<String> allowed) {
        if (allowed.isEmpty()) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 未配置可选值");
        }
        String v = String.valueOf(value);
        if (!allowed.contains(v)) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID,
                    label + " 的值不在可选范围内（允许: " + String.join("、", allowed) + "）");
        }
    }

    private void validateMultiOptions(String label, Object value, Set<String> allowed) {
        if (allowed.isEmpty()) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 未配置可选值");
        }
        List<String> values = toStringList(value);
        for (String v : values) {
            if (!allowed.contains(v)) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID,
                        label + " 含有非法选项: " + v);
            }
        }
    }

    private void validateUpload(String label, Object value) {
        List<?> list;
        try {
            if (value instanceof List<?> l) {
                list = l;
            } else if (value instanceof String s) {
                list = objectMapper.readValue(s, List.class);
            } else {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 附件格式不正确");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 附件格式不正确");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 附件项格式不正确");
            }
            Object url = map.get("url");
            Object name = map.get("name");
            if (url == null || String.valueOf(url).isBlank()) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 附件缺少 url");
            }
            if (name == null || String.valueOf(name).isBlank()) {
                throw new BizException(ErrorCode.DATA_POOL_FIELD_INVALID, label + " 附件缺少文件名");
            }
        }
    }

    private Set<String> optionValues(DataModelSchema.FieldDef field) {
        Object options = field.getProps() != null ? field.getProps().get("options") : null;
        if (!(options instanceof Collection<?> col)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : col) {
            if (item instanceof Map<?, ?> map) {
                Object v = map.get("value");
                if (v != null) {
                    values.add(String.valueOf(v));
                }
            } else if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> col) {
            return col.stream().map(String::valueOf).collect(Collectors.toList());
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.startsWith("[")) {
                try {
                    List<Object> list = objectMapper.readValue(trimmed, List.class);
                    return list.stream().map(String::valueOf).collect(Collectors.toList());
                } catch (Exception ignored) {
                    // fall through
                }
            }
            if (trimmed.contains(",")) {
                return Arrays.stream(trimmed.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
            }
            return List.of(trimmed);
        }
        return List.of(String.valueOf(value));
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }
}
