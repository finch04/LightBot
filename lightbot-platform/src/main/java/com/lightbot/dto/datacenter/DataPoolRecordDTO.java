package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 数据池单条记录（字段 key -> 值）
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataPoolRecordDTO {

    @NotNull(message = "记录数据不能为空")
    private Map<String, Object> data;
}
