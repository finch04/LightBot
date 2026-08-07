package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据池批量新增
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataPoolBatchCreateDTO {

    @NotEmpty(message = "批量数据不能为空")
    private List<Map<String, Object>> records;
}
