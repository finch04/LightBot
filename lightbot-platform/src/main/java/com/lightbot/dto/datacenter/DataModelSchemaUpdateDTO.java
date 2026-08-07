package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存表单结构 / 索引与检索配置
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataModelSchemaUpdateDTO {

    @NotNull(message = "schema 不能为空")
    private DataModelSchema schema;
}
